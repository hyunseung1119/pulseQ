package com.pulseq.infra.persistence.entity

import com.pulseq.core.domain.QueueEntry
import com.pulseq.core.domain.QueueStatus
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "queue_entries")
class QueueEntryEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "event_id", nullable = false)
    val eventId: UUID,

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "queue_ticket", nullable = false, unique = true)
    val queueTicket: String,

    @Column(nullable = false)
    val position: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: QueueStatus = QueueStatus.WAITING,

    @Column(name = "bot_score")
    val botScore: Double? = null,

    @Column(name = "blocked_reason")
    val blockedReason: String? = null,

    @Column(name = "ip_address")
    val ipAddress: String? = null,

    @Column(name = "user_agent", columnDefinition = "TEXT")
    val userAgent: String? = null,

    @Column
    val fingerprint: String? = null,

    @Column(name = "entered_at", nullable = false)
    val enteredAt: Instant = Instant.now(),

    @Column(name = "processed_at")
    val processedAt: Instant? = null,

    @Column(name = "completed_at")
    val completedAt: Instant? = null,

    @Column(name = "entry_token")
    val entryToken: String? = null,

    @Column(name = "entry_token_expires_at")
    val entryTokenExpiresAt: Instant? = null
) {
    fun toDomain(): QueueEntry = QueueEntry(
        id = id,
        eventId = eventId,
        userId = userId,
        queueTicket = queueTicket,
        position = position,
        status = status,
        botScore = botScore,
        blockedReason = blockedReason,
        ipAddress = ipAddress,
        userAgent = userAgent,
        fingerprint = fingerprint,
        enteredAt = enteredAt,
        processedAt = processedAt,
        completedAt = completedAt,
        entryToken = entryToken,
        entryTokenExpiresAt = entryTokenExpiresAt
    )

    companion object {
        fun from(entry: QueueEntry): QueueEntryEntity = QueueEntryEntity(
            id = entry.id,
            eventId = entry.eventId,
            userId = entry.userId,
            queueTicket = entry.queueTicket,
            position = entry.position,
            status = entry.status,
            botScore = entry.botScore,
            blockedReason = entry.blockedReason,
            ipAddress = entry.ipAddress,
            userAgent = entry.userAgent,
            fingerprint = entry.fingerprint,
            enteredAt = entry.enteredAt,
            processedAt = entry.processedAt,
            completedAt = entry.completedAt,
            entryToken = entry.entryToken,
            entryTokenExpiresAt = entry.entryTokenExpiresAt
        )
    }
}
