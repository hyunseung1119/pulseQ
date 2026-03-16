package com.pulseq.core.domain

import java.time.Instant
import java.util.UUID

data class Event(
    val id: UUID = UUID.randomUUID(),
    val tenantId: UUID,
    val name: String,
    val slug: String,
    val status: EventStatus = EventStatus.SCHEDULED,
    val maxCapacity: Int,
    val rateLimit: Int = 100,
    val entryTokenTtlSeconds: Int = 300,
    val startAt: Instant,
    val endAt: Instant,
    val botDetectionEnabled: Boolean = true,
    val botScoreThreshold: Double = 0.80,
    val webhookUrl: String? = null,
    val totalEntered: Int = 0,
    val totalProcessed: Int = 0,
    val totalAbandoned: Int = 0,
    val totalBotBlocked: Int = 0,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    fun isActive(): Boolean = status == EventStatus.ACTIVE

    fun isAcceptingEntries(): Boolean =
        isActive() && totalEntered < maxCapacity
}

enum class EventStatus {
    SCHEDULED, ACTIVE, PAUSED, COMPLETED, CANCELLED
}
