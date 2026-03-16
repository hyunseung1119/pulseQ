package com.pulseq.api.dto

import com.pulseq.core.domain.EntryToken
import com.pulseq.core.domain.QueueEntry
import com.pulseq.core.domain.QueuePosition
import com.pulseq.core.domain.QueueStats
import com.pulseq.core.domain.QueueStatus
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class QueueEnterRequest(
    @field:NotBlank(message = "userId is required")
    val userId: String,
    val metadata: QueueMetadata? = null
)

data class QueueMetadata(
    val ip: String? = null,
    val userAgent: String? = null,
    val fingerprint: String? = null
)

data class QueueEnterResponse(
    val queueTicket: String,
    val position: Long,
    val estimatedWaitSeconds: Long,
    val totalAhead: Long,
    val status: QueueStatus,
    val enteredAt: Instant
) {
    companion object {
        fun from(entry: QueueEntry, rateLimit: Int): QueueEnterResponse = QueueEnterResponse(
            queueTicket = entry.queueTicket,
            position = entry.position,
            estimatedWaitSeconds = if (rateLimit > 0) entry.position / rateLimit else 0,
            totalAhead = entry.position,
            status = entry.status,
            enteredAt = entry.enteredAt
        )
    }
}

data class QueuePositionResponse(
    val queueTicket: String,
    val position: Long,
    val estimatedWaitSeconds: Long,
    val totalAhead: Long,
    val totalBehind: Long,
    val status: QueueStatus,
    val ratePerSecond: Int
) {
    companion object {
        fun from(pos: QueuePosition): QueuePositionResponse = QueuePositionResponse(
            queueTicket = pos.queueTicket,
            position = pos.position,
            estimatedWaitSeconds = pos.estimatedWaitSeconds,
            totalAhead = pos.totalAhead,
            totalBehind = pos.totalBehind,
            status = pos.status,
            ratePerSecond = pos.ratePerSecond
        )
    }
}

data class QueueStatusResponse(
    val eventId: UUID,
    val status: String,
    val totalWaiting: Long,
    val totalProcessed: Long,
    val totalAbandoned: Long,
    val currentRatePerSecond: Int,
    val estimatedClearTimeSeconds: Long,
    val botBlocked: Long
) {
    companion object {
        fun from(stats: QueueStats): QueueStatusResponse = QueueStatusResponse(
            eventId = stats.eventId,
            status = stats.status.name,
            totalWaiting = stats.totalWaiting,
            totalProcessed = stats.totalProcessed,
            totalAbandoned = stats.totalAbandoned,
            currentRatePerSecond = stats.currentRatePerSecond,
            estimatedClearTimeSeconds = stats.estimatedClearTimeSeconds,
            botBlocked = stats.botBlocked
        )
    }
}

data class VerifyTokenRequest(
    @field:NotBlank(message = "entryToken is required")
    val entryToken: String
)

data class VerifyTokenResponse(
    val valid: Boolean,
    val userId: String,
    val expiresAt: Instant,
    val remainingSeconds: Long
) {
    companion object {
        fun from(token: EntryToken): VerifyTokenResponse = VerifyTokenResponse(
            valid = token.isValid(),
            userId = token.userId,
            expiresAt = token.expiresAt,
            remainingSeconds = token.remainingSeconds()
        )
    }
}

data class QueueLeaveResponse(
    val queueTicket: String,
    val status: QueueStatus,
    val leftAt: Instant
)
