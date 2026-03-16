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

@Component
class RedisQueueAdapter(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val redissonClient: RedissonClient,
    private val objectMapper: ObjectMapper
) : QueuePort {

    private fun queueKey(eventId: UUID) = "queue:$eventId"
    private fun entryTokenKey(token: String) = "entry_token:$token"
    private fun botScoreKey(eventId: UUID, userId: String) = "bot_score:$eventId:$userId"

    override suspend fun enqueue(eventId: UUID, userId: String, score: Double): Boolean {
        val ops = redisTemplate.opsForZSet()
        val added = ops.add(queueKey(eventId), userId, score).awaitSingleOrNull()
        return added == true
    }

    override suspend fun getPosition(eventId: UUID, userId: String): Long? {
        val ops = redisTemplate.opsForZSet()
        return ops.rank(queueKey(eventId), userId).awaitSingleOrNull()
    }

    override suspend fun getQueueSize(eventId: UUID): Long {
        val ops = redisTemplate.opsForZSet()
        return ops.size(queueKey(eventId)).awaitSingle()
    }

    override suspend fun dequeueTop(eventId: UUID, count: Long): List<String> {
        val ops = redisTemplate.opsForZSet()
        val key = queueKey(eventId)
        val results = ops.popMin(key, count).collectList().awaitSingle()
        return results.mapNotNull { it.value }
    }

    override suspend fun remove(eventId: UUID, userId: String): Boolean {
        val ops = redisTemplate.opsForZSet()
        val removed = ops.remove(queueKey(eventId), userId).awaitSingle()
        return removed > 0L
    }

    override suspend fun isMember(eventId: UUID, userId: String): Boolean {
        return getPosition(eventId, userId) != null
    }

    override suspend fun saveEntryToken(token: EntryToken, ttlSeconds: Long) {
        val ops = redisTemplate.opsForValue()
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

    override suspend fun getEntryToken(token: String): EntryToken? {
        val ops = redisTemplate.opsForValue()
        val json = ops.get(entryTokenKey(token)).awaitSingleOrNull() ?: return null

        val map = objectMapper.readValue(json, Map::class.java)
        return EntryToken(
            token = map["token"] as String,
            userId = map["userId"] as String,
            eventId = UUID.fromString(map["eventId"] as String),
            expiresAt = Instant.parse(map["expiresAt"] as String)
        )
    }

    override suspend fun deleteEntryToken(token: String) {
        redisTemplate.delete(entryTokenKey(token)).awaitSingle()
    }

    override suspend fun <T> withLock(
        lockKey: String,
        waitMs: Long,
        leaseMs: Long,
        block: suspend () -> T
    ): T = withContext(Dispatchers.IO) {
        val lock = redissonClient.getLock(lockKey)
        val acquired = lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS)

        if (!acquired) {
            throw IllegalStateException("Failed to acquire lock: $lockKey")
        }

        try {
            block()
        } finally {
            if (lock.isHeldByCurrentThread) {
                lock.unlock()
            }
        }
    }

    override suspend fun setBotScore(eventId: UUID, userId: String, score: Double, ttlSeconds: Long) {
        val ops = redisTemplate.opsForValue()
        ops.set(
            botScoreKey(eventId, userId),
            score.toString(),
            Duration.ofSeconds(ttlSeconds)
        ).awaitSingle()
    }

    override suspend fun getBotScore(eventId: UUID, userId: String): Double? {
        val ops = redisTemplate.opsForValue()
        return ops.get(botScoreKey(eventId, userId)).awaitSingleOrNull()?.toDoubleOrNull()
    }
}
