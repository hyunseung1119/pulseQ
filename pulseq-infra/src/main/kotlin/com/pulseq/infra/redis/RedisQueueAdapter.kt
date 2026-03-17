package com.pulseq.infra.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.pulseq.core.domain.EntryToken
import com.pulseq.core.port.QueuePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.withContext
import org.redisson.api.RedissonClient
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Redis 대기열 어댑터 — QueuePort 인터페이스의 구현체.
 * Redis Sorted Set(ZADD/ZRANK/ZPOPMIN)으로 대기열을 관리하고,
 * Redisson 분산 락으로 동시성을 제어한다.
 */
@Component
class RedisQueueAdapter(
    private val redisTemplate: ReactiveStringRedisTemplate,  // Spring Reactive Redis 클라이언트
    private val redissonClient: RedissonClient,              // Redisson 분산 락 클라이언트
    private val objectMapper: ObjectMapper                   // JSON 직렬화/역직렬화
) : QueuePort {

    // Redis 키 네이밍 컨벤션: 용도:식별자
    private fun queueKey(eventId: UUID) = "queue:$eventId"                              // 대기열 Sorted Set 키
    private fun entryTokenKey(token: String) = "entry_token:$token"                     // 입장 토큰 키 (TTL 자동 만료)
    private fun botScoreKey(eventId: UUID, userId: String) = "bot_score:$eventId:$userId" // 봇 스코어 캐시 키

    /**
     * 대기열 입장 — ZADD queue:{eventId} {score} {userId}
     * score = 타임스탬프(ms) → 먼저 들어온 사람이 낮은 점수 → 먼저 나감 (FIFO)
     * ZADD는 원자적이므로 같은 userId가 이미 있으면 false 반환 (중복 방지)
     */
    override suspend fun enqueue(eventId: UUID, userId: String, score: Double): Boolean {
        val ops = redisTemplate.opsForZSet()
        val added = ops.add(queueKey(eventId), userId, score).awaitSingleOrNull()
        return added == true
    }

    /**
     * 대기 위치 조회 — ZRANK queue:{eventId} {userId}
     * O(log N) 시간복잡도. 0-based index 반환 (0 = 가장 앞)
     * 존재하지 않으면 null 반환
     */
    override suspend fun getPosition(eventId: UUID, userId: String): Long? {
        val ops = redisTemplate.opsForZSet()
        return ops.rank(queueKey(eventId), userId).awaitSingleOrNull()
    }

    /**
     * 대기열 크기 조회 — ZCARD queue:{eventId}
     * 현재 대기 중인 총 인원 수 반환
     */
    override suspend fun getQueueSize(eventId: UUID): Long {
        val ops = redisTemplate.opsForZSet()
        return ops.size(queueKey(eventId)).awaitSingle()
    }

    /**
     * 상위 N명 추출 — ZPOPMIN queue:{eventId} {count}
     * 점수가 가장 낮은(= 가장 먼저 입장한) N명을 원자적으로 꺼낸다.
     * 꺼낸 유저는 Sorted Set에서 제거된다 (FIFO 큐 동작).
     */
    override suspend fun dequeueTop(eventId: UUID, count: Long): List<String> {
        val ops = redisTemplate.opsForZSet()
        val key = queueKey(eventId)
        // ZPOPMIN: score 오름차순으로 count개 팝
        val results = ops.popMin(key, count).collectList().awaitSingle()
        return results.mapNotNull { it.value }
    }

    /**
     * 대기열에서 유저 제거 — ZREM queue:{eventId} {userId}
     * 유저가 자발적으로 퇴장할 때 호출
     */
    override suspend fun remove(eventId: UUID, userId: String): Boolean {
        val ops = redisTemplate.opsForZSet()
        val removed = ops.remove(queueKey(eventId), userId).awaitSingle()
        return removed > 0L
    }

    /**
     * 대기열 멤버 여부 확인 — ZRANK 결과가 null이 아니면 멤버
     */
    override suspend fun isMember(eventId: UUID, userId: String): Boolean {
        return getPosition(eventId, userId) != null
    }

    /**
     * 입장 토큰 저장 — SET entry_token:{token} {json} EX {ttl}
     * TTL이 지나면 Redis에서 자동 삭제 → 만료된 토큰은 조회 불가
     */
    override suspend fun saveEntryToken(token: EntryToken, ttlSeconds: Long) {
        val ops = redisTemplate.opsForValue()
        // 토큰 정보를 JSON으로 직렬화하여 저장
        val json = objectMapper.writeValueAsString(
            mapOf(
                "token" to token.token,
                "userId" to token.userId,
                "eventId" to token.eventId.toString(),
                "expiresAt" to token.expiresAt.toString()
            )
        )
        ops.set(entryTokenKey(token.token), json, Duration.ofSeconds(ttlSeconds)).awaitSingle()
    }

    /**
     * 입장 토큰 조회 — GET entry_token:{token}
     * TTL 만료 시 null 반환 (자동 만료)
     */
    override suspend fun getEntryToken(token: String): EntryToken? {
        val ops = redisTemplate.opsForValue()
        val json = ops.get(entryTokenKey(token)).awaitSingleOrNull() ?: return null

        // JSON을 EntryToken 도메인 객체로 역직렬화
        val map = objectMapper.readValue(json, Map::class.java)
        return EntryToken(
            token = map["token"] as String,
            userId = map["userId"] as String,
            eventId = UUID.fromString(map["eventId"] as String),
            expiresAt = Instant.parse(map["expiresAt"] as String)
        )
    }

    /**
     * 입장 토큰 삭제 — DEL entry_token:{token}
     */
    override suspend fun deleteEntryToken(token: String) {
        redisTemplate.delete(entryTokenKey(token)).awaitSingle()
    }

    /**
     * Redisson 분산 락 — tryLock으로 락 획득 시도, 실패 시 예외 발생.
     * Dispatchers.IO에서 실행하여 코루틴 이벤트 루프 블로킹 방지.
     *
     * @param lockKey  락 키 (예: "queue:enter:{eventId}:{userId}")
     * @param waitMs   락 획득 대기 시간 (기본 5초, 초과 시 실패)
     * @param leaseMs  락 보유 시간 (기본 10초, 초과 시 자동 해제 → 좀비 락 방지)
     * @param block    락 내에서 실행할 임계 영역 코드
     */
    override suspend fun <T> withLock(
        lockKey: String,
        waitMs: Long,
        leaseMs: Long,
        block: suspend () -> T
    ): T = withContext(Dispatchers.IO) {
        val lock = redissonClient.getLock(lockKey)
        // tryLock: 비블로킹 락 획득 시도 (waitMs 동안 재시도)
        val acquired = lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS)

        if (!acquired) {
            throw IllegalStateException("Failed to acquire lock: $lockKey")
        }

        try {
            block()  // 임계 영역 실행
        } finally {
            // 현재 스레드가 락을 보유하고 있을 때만 해제 (다른 스레드의 락 해제 방지)
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    /**
     * 봇 스코어 캐싱 — SET bot_score:{eventId}:{userId} {score} EX {ttl}
     * 같은 유저의 반복 요청에 대해 ML 서비스 재호출 방지 (TTL 10분)
     */
    override suspend fun setBotScore(eventId: UUID, userId: String, score: Double, ttlSeconds: Long) {
        val ops = redisTemplate.opsForValue()
        ops.set(
            botScoreKey(eventId, userId),
            score.toString(),
            Duration.ofSeconds(ttlSeconds)
        ).awaitSingle()
    }

    /**
     * 봇 스코어 캐시 조회 — GET bot_score:{eventId}:{userId}
     * 캐시 히트 시 ML 서비스 호출 생략 (sub-ms 응답)
     */
    override suspend fun getBotScore(eventId: UUID, userId: String): Double? {
        val ops = redisTemplate.opsForValue()
        return ops.get(botScoreKey(eventId, userId)).awaitSingleOrNull()?.toDoubleOrNull()
    }
}
