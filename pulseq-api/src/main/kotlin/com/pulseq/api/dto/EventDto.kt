package com.pulseq.api.dto

import com.pulseq.core.domain.Event
import com.pulseq.core.domain.EventStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.util.UUID

data class CreateEventRequest(
    @field:NotBlank(message = "Event name is required")
    val name: String,

    @field:NotBlank(message = "Slug is required")
    @field:Pattern(regexp = "^[a-z0-9-]+$", message = "Slug must contain only lowercase letters, numbers, and hyphens")
    val slug: String,

    @field:Min(1, message = "Max capacity must be at least 1")
    @field:Max(1_000_000, message = "Max capacity cannot exceed 1,000,000")
    val maxCapacity: Int,

    val startAt: Instant,
    val endAt: Instant,

    @field:Min(1) @field:Max(10_000)
    val rateLimit: Int = 100,

    @field:Min(60) @field:Max(600)
    val entryTokenTtlSeconds: Int = 300,

    val config: EventConfig = EventConfig()
)

data class EventConfig(
    val botDetectionEnabled: Boolean = true,
    val botScoreThreshold: Double = 0.80,
    val webhookUrl: String? = null
)

data class UpdateEventRequest(
    val name: String? = null,
    val maxCapacity: Int? = null,
    val rateLimit: Int? = null,
    val entryTokenTtlSeconds: Int? = null,
    val botDetectionEnabled: Boolean? = null,
    val botScoreThreshold: Double? = null,
    val webhookUrl: String? = null
)

data class EventResponse(
    val eventId: UUID,
    val name: String,
    val slug: String,
    val status: EventStatus,
    val maxCapacity: Int,
    val rateLimit: Int,
    val entryTokenTtlSeconds: Int,
    val startAt: Instant,
    val endAt: Instant,
    val botDetectionEnabled: Boolean,
    val totalEntered: Int,
    val totalProcessed: Int,
    val createdAt: Instant
) {
    companion object {
        fun from(event: Event): EventResponse = EventResponse(
            eventId = event.id,
            name = event.name,
            slug = event.slug,
            status = event.status,
            maxCapacity = event.maxCapacity,
            rateLimit = event.rateLimit,
            entryTokenTtlSeconds = event.entryTokenTtlSeconds,
            startAt = event.startAt,
            endAt = event.endAt,
            botDetectionEnabled = event.botDetectionEnabled,
            totalEntered = event.totalEntered,
            totalProcessed = event.totalProcessed,
            createdAt = event.createdAt
        )
    }
}

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)
