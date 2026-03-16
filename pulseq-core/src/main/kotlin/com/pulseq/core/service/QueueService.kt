package com.pulseq.core.service

import com.pulseq.core.domain.*
import com.pulseq.core.exception.*
import com.pulseq.core.port.EventPublisher
import com.pulseq.core.port.EventRepository
import com.pulseq.core.port.QueueEntryRepository
import com.pulseq.core.port.QueuePort
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

@Service
class QueueService(
    private val queuePort: QueuePort,
    private val eventRepository: EventRepository,
    private val queueEntryRepository: QueueEntryRepository,
    private val eventPublisher: EventPublisher,
    private val botDetectionService: BotDetectionService
) {
    private val secureRandom = SecureRandom()

    suspend fun enter(
        eventId: UUID,
        tenantId: UUID,
        userId: String,
        ipAddress: String? = null,
        userAgent: String? = null,
        fingerprint: String? = null
    ): QueueEntry {
        val event = getActiveEvent(eventId, tenantId)

        // 중복 입장 체크 (Redis + DB 모두 확인)
        val existingEntry = queueEntryRepository.findByEventIdAndUserId(eventId, userId)
        if (existingEntry != null && existingEntry.status in listOf(QueueStatus.WAITING, QueueStatus.PROCESSING)) {
            throw AlreadyInQueueException(userId, existingEntry.queueTicket)
        }
        if (queuePort.isMember(eventId, userId)) {
            throw AlreadyInQueueException(userId, existingEntry?.queueTicket ?: "unknown")
        }

        // 봇 탐지 체크
        if (event.botDetectionEnabled) {
            val botResult = botDetectionService.checkBotScore(
                eventId = eventId,
                userId = userId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                fingerprint = fingerprint,
                botScoreThreshold = event.botScoreThreshold.toDouble()
            )
            if (botResult.isBot) {
                // 봇 차단 카운터 + Kafka 이벤트
                eventRepository.update(event.copy(
                    totalBotBlocked = event.totalBotBlocked + 1,
                    updatedAt = Instant.now()
                ))
                eventPublisher.publish(QueueEventLog(
                    eventId = eventId,
                    userId = userId,
                    eventType = QueueEventType.BOT_BLOCKED,
                    payload = mapOf(
                        "botScore" to botResult.botScore,
                        "reasons" to botResult.topReasons.map { it.feature }
                    )
                ))
                throw BotDetectedException(userId, botResult.botScore)
            }
        }

        // 정원 초과 체크
        val currentSize = queuePort.getQueueSize(eventId)
        if (currentSize >= event.maxCapacity) {
            throw QueueFullException(eventId.toString(), event.maxCapacity)
        }

        // 분산 락 내에서 입장 처리
        return queuePort.withLock("queue:enter:$eventId") {
            val score = Instant.now().toEpochMilli().toDouble()
            val enqueued = queuePort.enqueue(eventId, userId, score)

            if (!enqueued) {
                val existing = queueEntryRepository.findByEventIdAndUserId(eventId, userId)
                throw AlreadyInQueueException(userId, existing?.queueTicket ?: "unknown")
            }

            val position = queuePort.getPosition(eventId, userId) ?: 0L
            val ticket = generateTicket()

            val entry = QueueEntry(
                eventId = eventId,
                userId = userId,
                queueTicket = ticket,
                position = position,
                status = QueueStatus.WAITING,
                ipAddress = ipAddress,
                userAgent = userAgent,
                fingerprint = fingerprint
            )

            // DB에 영구 저장
            queueEntryRepository.save(entry)

            // 이벤트 카운터 업데이트
            eventRepository.update(event.copy(
                totalEntered = event.totalEntered + 1,
                updatedAt = Instant.now()
            ))

            // Kafka 이벤트 발행
            eventPublisher.publish(QueueEventLog(
                eventId = eventId,
                userId = userId,
                eventType = QueueEventType.QUEUE_ENTERED,
                payload = mapOf(
                    "position" to position,
                    "queueTicket" to ticket,
                    "ipAddress" to ipAddress,
                    "userAgent" to userAgent
                )
            ))

            entry
        }
    }

    suspend fun getPosition(
        eventId: UUID,
        tenantId: UUID,
        queueTicket: String
    ): QueuePosition {
        val event = getEventForTenant(eventId, tenantId)
        val entry = queueEntryRepository.findByQueueTicket(queueTicket)
            ?: throw EventNotFoundException(eventId.toString())

        val position = queuePort.getPosition(eventId, entry.userId) ?: 0L
        val totalSize = queuePort.getQueueSize(eventId)

        return QueuePosition(
            queueTicket = queueTicket,
            position = position,
            estimatedWaitSeconds = if (event.rateLimit > 0) position / event.rateLimit else 0,
            totalAhead = position,
            totalBehind = (totalSize - position - 1).coerceAtLeast(0),
            status = entry.status,
            ratePerSecond = event.rateLimit
        )
    }

    suspend fun getQueueStatus(eventId: UUID, tenantId: UUID): QueueStats {
        val event = getEventForTenant(eventId, tenantId)
        val waiting = queuePort.getQueueSize(eventId)

        return QueueStats(
            eventId = eventId,
            status = event.status,
            totalWaiting = waiting,
            totalProcessed = event.totalProcessed.toLong(),
            totalAbandoned = event.totalAbandoned.toLong(),
            currentRatePerSecond = event.rateLimit,
            estimatedClearTimeSeconds = if (event.rateLimit > 0) waiting / event.rateLimit else 0,
            botBlocked = event.totalBotBlocked.toLong()
        )
    }

    /**
     * 상위 N명에게 입장 토큰을 발급한다.
     * 스케줄러에서 주기적으로 호출됨.
     */
    suspend fun processQueue(eventId: UUID): List<EntryToken> {
        val event = eventRepository.findById(eventId)
            ?: throw EventNotFoundException(eventId.toString())

        if (!event.isActive()) return emptyList()

        val batchSize = event.rateLimit.toLong()
        val userIds = queuePort.dequeueTop(eventId, batchSize)

        if (userIds.isEmpty()) return emptyList()

        val tokens = userIds.map { userId ->
            val token = generateEntryToken()
            val entryToken = EntryToken(
                token = token,
                userId = userId,
                eventId = eventId,
                expiresAt = Instant.now().plusSeconds(event.entryTokenTtlSeconds.toLong())
            )

            // Redis에 입장 토큰 저장
            queuePort.saveEntryToken(entryToken, event.entryTokenTtlSeconds.toLong())

            // DB 상태 업데이트
            val entry = queueEntryRepository.findByEventIdAndUserId(eventId, userId)
            if (entry != null) {
                queueEntryRepository.update(entry.copy(
                    status = QueueStatus.PROCESSING,
                    processedAt = Instant.now(),
                    entryToken = token,
                    entryTokenExpiresAt = entryToken.expiresAt
                ))
            }

            entryToken
        }

        // 이벤트 처리 카운터 업데이트
        eventRepository.update(event.copy(
            totalProcessed = event.totalProcessed + tokens.size,
            updatedAt = Instant.now()
        ))

        // Kafka 배치 이벤트 발행
        eventPublisher.publishBatch(tokens.map { token ->
            QueueEventLog(
                eventId = eventId,
                userId = token.userId,
                eventType = QueueEventType.ENTRY_GRANTED,
                payload = mapOf(
                    "entryToken" to token.token,
                    "expiresAt" to token.expiresAt.toString()
                )
            )
        })

        return tokens
    }

    suspend fun verifyEntryToken(eventId: UUID, tenantId: UUID, token: String): EntryToken {
        getEventForTenant(eventId, tenantId)
        val entryToken = queuePort.getEntryToken(token)
            ?: throw EventNotFoundException("Entry token not found or expired")

        if (entryToken.eventId != eventId) {
            throw EventNotFoundException("Entry token does not belong to this event")
        }

        // Kafka 이벤트 발행
        eventPublisher.publish(QueueEventLog(
            eventId = eventId,
            userId = entryToken.userId,
            eventType = QueueEventType.ENTRY_VERIFIED,
            payload = mapOf("token" to token)
        ))

        return entryToken
    }

    suspend fun leave(eventId: UUID, tenantId: UUID, queueTicket: String): QueueEntry {
        val event = getEventForTenant(eventId, tenantId)
        val entry = queueEntryRepository.findByQueueTicket(queueTicket)
            ?: throw EventNotFoundException("Queue ticket not found")

        // Redis에서 제거
        queuePort.remove(eventId, entry.userId)

        // DB 상태 업데이트
        val updated = entry.copy(
            status = QueueStatus.ABANDONED,
            completedAt = Instant.now()
        )
        queueEntryRepository.update(updated)

        // 이벤트 이탈 카운터 업데이트
        eventRepository.update(event.copy(
            totalAbandoned = event.totalAbandoned + 1,
            updatedAt = Instant.now()
        ))

        // Kafka 이벤트 발행
        eventPublisher.publish(QueueEventLog(
            eventId = eventId,
            userId = entry.userId,
            eventType = QueueEventType.QUEUE_LEFT,
            payload = mapOf(
                "queueTicket" to queueTicket,
                "waitDurationMs" to (Instant.now().toEpochMilli() - entry.enteredAt.toEpochMilli())
            )
        ))

        return updated
    }

    private suspend fun getActiveEvent(eventId: UUID, tenantId: UUID): Event {
        val event = getEventForTenant(eventId, tenantId)
        if (!event.isActive()) {
            throw EventNotActiveException(eventId.toString())
        }
        return event
    }

    private suspend fun getEventForTenant(eventId: UUID, tenantId: UUID): Event {
        val event = eventRepository.findById(eventId)
            ?: throw EventNotFoundException(eventId.toString())
        if (event.tenantId != tenantId) {
            throw EventNotFoundException(eventId.toString())
        }
        return event
    }

    private fun generateTicket(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return "qt_${Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)}"
    }

    private fun generateEntryToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return "et_${Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)}"
    }
}
