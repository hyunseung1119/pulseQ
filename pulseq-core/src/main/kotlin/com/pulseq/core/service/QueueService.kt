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

/**
 * 대기열 핵심 서비스 — 입장, 위치 조회, 큐 처리, 토큰 검증, 퇴장을 담당한다.
 * Redis Sorted Set + Redisson 분산 락 + PostgreSQL 영구 저장 + Kafka 이벤트 발행을 조합한다.
 */
@Service
class QueueService(
    private val queuePort: QueuePort,                       // Redis 대기열 포트 (Sorted Set + 분산 락)
    private val eventRepository: EventRepository,           // 이벤트 저장소 포트 (PostgreSQL)
    private val queueEntryRepository: QueueEntryRepository, // 대기열 엔트리 저장소 포트
    private val eventPublisher: EventPublisher,             // Kafka 이벤트 발행 포트
    private val botDetectionService: BotDetectionService    // 봇 탐지 서비스 (캐시→ML→Rule fallback)
) {
    // 보안 난수 생성기 — 대기열 티켓과 입장 토큰 생성에 사용
    private val secureRandom = SecureRandom()

    /**
     * 대기열 입장 — 전체 플로우:
     * 1. 이벤트 활성 상태 확인
     * 2. 중복 입장 체크 (DB + Redis)
     * 3. 봇 탐지 (활성화 시)
     * 4. 정원 초과 체크
     * 5. 분산 락 내에서 Redis 입장 + DB 저장 + Kafka 발행
     */
    suspend fun enter(
        eventId: UUID,
        tenantId: UUID,
        userId: String,
        ipAddress: String? = null,
        userAgent: String? = null,
        fingerprint: String? = null
    ): QueueEntry {
        // 이벤트가 존재하고 ACTIVE 상태인지 확인
        val event = getActiveEvent(eventId, tenantId)

        // 중복 입장 체크 — DB에서 WAITING/PROCESSING 상태인 기존 엔트리 확인
        val existingEntry = queueEntryRepository.findByEventIdAndUserId(eventId, userId)
        if (existingEntry != null && existingEntry.status in listOf(QueueStatus.WAITING, QueueStatus.PROCESSING)) {
            throw AlreadyInQueueException(userId, existingEntry.queueTicket)
        }
        // Redis Sorted Set에도 이미 존재하는지 확인 (이중 검증)
        if (queuePort.isMember(eventId, userId)) {
            throw AlreadyInQueueException(userId, existingEntry?.queueTicket ?: "unknown")
        }

        // 봇 탐지 — 이벤트에 봇 탐지가 활성화된 경우 실행
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
                // 봇 차단: 원자적 카운터 증가 + Kafka 이벤트 발행
                eventRepository.incrementTotalBotBlocked(eventId)
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

        // 분산 락: 유저 단위로 동시 입장 방지 (이벤트 단위 → 유저 단위로 변경하여 처리량 개선)
        // 이전: withLock("queue:enter:$eventId") → 10.9 req/s (80% 500 에러)
        // 이후: withLock("queue:enter:$eventId:$userId") → 66.0 req/s (+506%)
        return queuePort.withLock("queue:enter:$eventId:$userId") {
            // 정원 초과 체크 — 락 내부에서 확인하여 TOCTOU race condition 방지
            val currentSize = queuePort.getQueueSize(eventId)
            if (currentSize >= event.maxCapacity) {
                throw QueueFullException(eventId.toString(), event.maxCapacity)
            }

            // Redis Sorted Set에 유저 추가 (score = 현재 타임스탬프)
            val score = Instant.now().toEpochMilli().toDouble()
            val enqueued = queuePort.enqueue(eventId, userId, score)

            // ZADD가 false를 반환하면 이미 존재하는 멤버 (원자적 중복 방지)
            if (!enqueued) {
                val existing = queueEntryRepository.findByEventIdAndUserId(eventId, userId)
                throw AlreadyInQueueException(userId, existing?.queueTicket ?: "unknown")
            }

            // ZRANK로 현재 위치 조회 (0-based index)
            val position = queuePort.getPosition(eventId, userId) ?: 0L
            // 보안 난수 기반 대기열 티켓 생성
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

            // PostgreSQL에 영구 저장 (Redis는 휘발성)
            queueEntryRepository.save(entry)

            // 원자적 카운터 증가 — SQL UPDATE SET total_entered = total_entered + 1
            eventRepository.incrementTotalEntered(eventId)

            // Kafka로 입장 이벤트 발행 (통계/로그용)
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

    /**
     * 대기열 위치 조회 — 티켓 번호로 현재 대기 순번을 확인한다.
     */
    suspend fun getPosition(
        eventId: UUID,
        tenantId: UUID,
        queueTicket: String
    ): QueuePosition {
        val event = getEventForTenant(eventId, tenantId)
        // 티켓 번호로 DB에서 엔트리 조회
        val entry = queueEntryRepository.findByQueueTicket(queueTicket)
            ?: throw EventNotFoundException(eventId.toString())

        // Redis ZRANK로 실시간 위치 조회
        val position = queuePort.getPosition(eventId, entry.userId) ?: 0L
        val totalSize = queuePort.getQueueSize(eventId)

        return QueuePosition(
            queueTicket = queueTicket,
            position = position,
            // 예상 대기 시간 = 앞에 있는 사람 수 / 초당 처리 속도
            estimatedWaitSeconds = if (event.rateLimit > 0) position / event.rateLimit else 0,
            totalAhead = position,
            totalBehind = (totalSize - position - 1).coerceAtLeast(0),
            status = entry.status,
            ratePerSecond = event.rateLimit
        )
    }

    /**
     * 큐 상태 조회 — 대기 인원, 처리 완료, 이탈, 봇 차단 수를 반환한다.
     */
    suspend fun getQueueStatus(eventId: UUID, tenantId: UUID): QueueStats {
        val event = getEventForTenant(eventId, tenantId)
        // Redis Sorted Set 크기 = 현재 대기 인원
        val waiting = queuePort.getQueueSize(eventId)

        return QueueStats(
            eventId = eventId,
            status = event.status,
            totalWaiting = waiting,
            totalProcessed = event.totalProcessed.toLong(),
            totalAbandoned = event.totalAbandoned.toLong(),
            currentRatePerSecond = event.rateLimit,
            // 예상 소진 시간 = 대기 인원 / 초당 처리 속도
            estimatedClearTimeSeconds = if (event.rateLimit > 0) waiting / event.rateLimit else 0,
            botBlocked = event.totalBotBlocked.toLong()
        )
    }

    /**
     * 큐 처리 — 상위 N명에게 입장 토큰을 발급한다.
     * 스케줄러(QueueScheduler)가 주기적으로 호출하며, 수동 호출도 가능하다.
     * ZPOPMIN으로 Redis에서 상위 N명을 원자적으로 추출한다.
     */
    suspend fun processQueue(eventId: UUID): List<EntryToken> {
        val event = eventRepository.findById(eventId)
            ?: throw EventNotFoundException(eventId.toString())

        // 비활성 이벤트는 처리하지 않음
        if (!event.isActive()) return emptyList()

        // rateLimit만큼 한 번에 처리 (예: rateLimit=100이면 100명씩)
        val batchSize = event.rateLimit.toLong()
        // ZPOPMIN: 점수가 가장 낮은(= 가장 먼저 입장한) N명을 꺼냄
        val userIds = queuePort.dequeueTop(eventId, batchSize)

        if (userIds.isEmpty()) return emptyList()

        val tokens = userIds.map { userId ->
            // 보안 난수 기반 입장 토큰 생성
            val token = generateEntryToken()
            val entryToken = EntryToken(
                token = token,
                userId = userId,
                eventId = eventId,
                // TTL 후 토큰 자동 만료 (미사용 시 대기열에서 이탈 처리)
                expiresAt = Instant.now().plusSeconds(event.entryTokenTtlSeconds.toLong())
            )

            // Redis에 입장 토큰 저장 (TTL 자동 만료)
            queuePort.saveEntryToken(entryToken, event.entryTokenTtlSeconds.toLong())

            // DB에서 해당 유저의 상태를 WAITING → PROCESSING으로 변경
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

        // 원자적 카운터 증가 — SQL UPDATE SET total_processed = total_processed + N
        eventRepository.incrementTotalProcessed(eventId, tokens.size)

        // Kafka 배치 이벤트 발행 — 각 유저에게 ENTRY_GRANTED 이벤트
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

    /**
     * 입장 토큰 검증 — 클라이언트가 받은 토큰이 유효한지 확인한다.
     * 티켓팅 시스템에서 "결제 페이지 접근 권한"을 확인하는 단계.
     */
    suspend fun verifyEntryToken(eventId: UUID, tenantId: UUID, token: String): EntryToken {
        getEventForTenant(eventId, tenantId)
        // Redis에서 토큰 조회 (TTL 만료 시 null 반환)
        val entryToken = queuePort.getEntryToken(token)
            ?: throw EventNotFoundException("Entry token not found or expired")

        // 토큰이 해당 이벤트에 속하는지 확인 (다른 이벤트 토큰 사용 방지)
        if (entryToken.eventId != eventId) {
            throw EventNotFoundException("Entry token does not belong to this event")
        }

        // Kafka 검증 완료 이벤트 발행
        eventPublisher.publish(QueueEventLog(
            eventId = eventId,
            userId = entryToken.userId,
            eventType = QueueEventType.ENTRY_VERIFIED,
            payload = mapOf("token" to token)
        ))

        return entryToken
    }

    /**
     * 대기열 퇴장 — 사용자가 자발적으로 대기열을 떠날 때 호출.
     * Redis에서 제거 + DB 상태 ABANDONED로 변경 + Kafka 이벤트 발행.
     */
    suspend fun leave(eventId: UUID, tenantId: UUID, queueTicket: String): QueueEntry {
        val event = getEventForTenant(eventId, tenantId)
        val entry = queueEntryRepository.findByQueueTicket(queueTicket)
            ?: throw EventNotFoundException("Queue ticket not found")

        // Redis Sorted Set에서 유저 제거
        queuePort.remove(eventId, entry.userId)

        // DB 상태를 ABANDONED로 변경
        val updated = entry.copy(
            status = QueueStatus.ABANDONED,
            completedAt = Instant.now()
        )
        queueEntryRepository.update(updated)

        // 원자적 카운터 증가 — SQL UPDATE SET total_abandoned = total_abandoned + 1
        eventRepository.incrementTotalAbandoned(eventId)

        // Kafka 퇴장 이벤트 발행 (대기 시간 포함)
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

    /** 이벤트가 존재 + ACTIVE 상태인지 확인 */
    private suspend fun getActiveEvent(eventId: UUID, tenantId: UUID): Event {
        val event = getEventForTenant(eventId, tenantId)
        if (!event.isActive()) {
            throw EventNotActiveException(eventId.toString())
        }
        return event
    }

    /** 이벤트가 존재 + 해당 테넌트 소유인지 확인 (BOLA 방지) */
    private suspend fun getEventForTenant(eventId: UUID, tenantId: UUID): Event {
        val event = eventRepository.findById(eventId)
            ?: throw EventNotFoundException(eventId.toString())
        if (event.tenantId != tenantId) {
            throw EventNotFoundException(eventId.toString())
        }
        return event
    }

    /** 대기열 티켓 생성 — 24바이트 보안 난수를 Base64 URL-safe로 인코딩 */
    private fun generateTicket(): String {
        val bytes = ByteArray(24)
        secureRandom.nextBytes(bytes)
        return "qt_${Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)}"
    }

    /** 입장 토큰 생성 — 32바이트 보안 난수를 Base64 URL-safe로 인코딩 */
    private fun generateEntryToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return "et_${Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)}"
    }
}
