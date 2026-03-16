package com.pulseq.core.port

import com.pulseq.core.domain.EntryToken
import java.util.UUID

/**
 * Redis 기반 대기열 연산 포트.
 * Sorted Set, 분산 락, 입장 토큰을 추상화한다.
 */
interface QueuePort {
    /** 대기열에 사용자 추가. 이미 존재하면 false 반환. */
    suspend fun enqueue(eventId: UUID, userId: String, score: Double): Boolean

    /** 현재 순번 조회 (0-based). 없으면 null. */
    suspend fun getPosition(eventId: UUID, userId: String): Long?

    /** 대기열 전체 크기 */
    suspend fun getQueueSize(eventId: UUID): Long

    /** 상위 N명 pop (입장 허가 대상) */
    suspend fun dequeueTop(eventId: UUID, count: Long): List<String>

    /** 대기열에서 사용자 제거 */
    suspend fun remove(eventId: UUID, userId: String): Boolean

    /** 사용자가 대기열에 있는지 확인 */
    suspend fun isMember(eventId: UUID, userId: String): Boolean

    /** 입장 토큰 저장 (TTL 적용) */
    suspend fun saveEntryToken(token: EntryToken, ttlSeconds: Long)

    /** 입장 토큰 조회 */
    suspend fun getEntryToken(token: String): EntryToken?

    /** 입장 토큰 삭제 (사용 완료) */
    suspend fun deleteEntryToken(token: String)

    /** 분산 락 획득 후 블록 실행 */
    suspend fun <T> withLock(lockKey: String, waitMs: Long = 5000, leaseMs: Long = 10000, block: suspend () -> T): T

    /** 봇 스코어 캐시 저장 */
    suspend fun setBotScore(eventId: UUID, userId: String, score: Double, ttlSeconds: Long = 600)

    /** 봇 스코어 캐시 조회 */
    suspend fun getBotScore(eventId: UUID, userId: String): Double?
}
