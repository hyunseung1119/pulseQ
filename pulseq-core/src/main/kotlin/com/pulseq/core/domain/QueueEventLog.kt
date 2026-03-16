package com.pulseq.core.domain

import java.time.Instant
import java.util.UUID

enum class QueueEventType {
    QUEUE_ENTERED,
    POSITION_UPDATED,
    ENTRY_GRANTED,
    ENTRY_VERIFIED,
    ENTRY_EXPIRED,
    QUEUE_LEFT,
    BOT_DETECTED,
    BOT_BLOCKED
}

data class QueueEventLog(
    val id: Long? = null,
    val eventId: UUID,
    val userId: String?,
    val eventType: QueueEventType,
    val payload: Map<String, Any?> = emptyMap(),
    val createdAt: Instant = Instant.now()
)
