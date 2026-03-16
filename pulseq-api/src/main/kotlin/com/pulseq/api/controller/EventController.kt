package com.pulseq.api.controller

import com.pulseq.api.dto.*
import com.pulseq.core.domain.EventStatus
import com.pulseq.core.service.EventService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/events")
class EventController(
    private val eventService: EventService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun create(
        @AuthenticationPrincipal tenantId: String,
        @Valid @RequestBody request: CreateEventRequest
    ): ApiResponse<EventResponse> {
        val event = eventService.create(
            tenantId = UUID.fromString(tenantId),
            name = request.name,
            slug = request.slug,
            maxCapacity = request.maxCapacity,
            startAt = request.startAt,
            endAt = request.endAt,
            rateLimit = request.rateLimit,
            entryTokenTtlSeconds = request.entryTokenTtlSeconds,
            botDetectionEnabled = request.config.botDetectionEnabled,
            botScoreThreshold = request.config.botScoreThreshold,
            webhookUrl = request.config.webhookUrl
        )
        return ApiResponse.ok(EventResponse.from(event))
    }

    @GetMapping
    suspend fun list(
        @AuthenticationPrincipal tenantId: String,
        @RequestParam(required = false) status: EventStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<PageResponse<EventResponse>> {
        val tid = UUID.fromString(tenantId)
        val events = eventService.findByTenantId(tid, status, page, size)
        val total = eventService.countByTenantId(tid)

        return ApiResponse.ok(
            PageResponse(
                content = events.map { EventResponse.from(it) },
                page = page,
                size = size,
                totalElements = total,
                totalPages = ((total + size - 1) / size).toInt()
            )
        )
    }

    @GetMapping("/{eventId}")
    suspend fun getById(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID
    ): ApiResponse<EventResponse> {
        val event = eventService.findById(eventId, UUID.fromString(tenantId))
        return ApiResponse.ok(EventResponse.from(event))
    }

    @PatchMapping("/{eventId}")
    suspend fun update(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID,
        @RequestBody request: UpdateEventRequest
    ): ApiResponse<EventResponse> {
        val event = eventService.update(
            eventId = eventId,
            tenantId = UUID.fromString(tenantId),
            name = request.name,
            maxCapacity = request.maxCapacity,
            rateLimit = request.rateLimit,
            entryTokenTtlSeconds = request.entryTokenTtlSeconds,
            botDetectionEnabled = request.botDetectionEnabled,
            botScoreThreshold = request.botScoreThreshold,
            webhookUrl = request.webhookUrl
        )
        return ApiResponse.ok(EventResponse.from(event))
    }

    @DeleteMapping("/{eventId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun delete(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID
    ) {
        eventService.delete(eventId, UUID.fromString(tenantId))
    }
}
