package com.pulseq.core.domain

import java.time.Instant
import java.util.UUID

data class QueueEntry(
    val id: UUID = UUID.randomUUID(),
    val eventId: UUID,
    val userId: String,
    val queueTicket: String,
    val position: Long,
    val status: QueueStatus = QueueStatus.WAITING,
    val botScore: Double? = null,
    val blockedReason: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val fingerprint: String? = null,
    val enteredAt: Instant = Instant.now(),
    val processedAt: Instant? = null,
    val completedAt: Instant? = null,
    val entryToken: String? = null,
    val entryTokenExpiresAt: Instant? = null
)

enum class QueueStatus {
    WAITING, PROCESSING, COMPLETED, ABANDONED, BLOCKED
}

data class QueuePosition(
    val queueTicket: String,
    val position: Long,
    val estimatedWaitSeconds: Long,
    val totalAhead: Long,
    val totalBehind: Long,
    val status: QueueStatus,
    val ratePerSecond: Int
)

data class QueueStats(
    val eventId: UUID,
    val status: EventStatus,
    val totalWaiting: Long,
    val totalProcessed: Long,
    val totalAbandoned: Long,
    val currentRatePerSecond: Int,
    val estimatedClearTimeSeconds: Long,
    val botBlocked: Long
)

data class EntryToken(
    val token: String,
    val userId: String,
    val eventId: UUID,
    val expiresAt: Instant
) {
    fun isValid(): Boolean = Instant.now().isBefore(expiresAt)
    fun remainingSeconds(): Long = java.time.Duration.between(Instant.now(), expiresAt).seconds.coerceAtLeast(0)
}
