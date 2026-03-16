package com.pulseq.api.controller

import com.pulseq.api.dto.ApiResponse
import com.pulseq.core.domain.QueueEventType
import com.pulseq.core.port.QueueEventLogRepository
import com.pulseq.core.service.EventService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@RestController
@RequestMapping("/api/v1/stats")
class StatsController(
    private val eventService: EventService,
    private val queueEventLogRepository: QueueEventLogRepository
) {
    @GetMapping("/{eventId}")
    suspend fun getEventStats(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID
    ): ApiResponse<EventStatsResponse> {
        val tid = UUID.fromString(tenantId)
        eventService.findById(eventId, tid)

        val now = Instant.now()
        val oneMinuteAgo = now.minus(1, ChronoUnit.MINUTES)
        val oneHourAgo = now.minus(1, ChronoUnit.HOURS)

        val totalEntered = queueEventLogRepository.countByEventIdAndType(eventId, QueueEventType.QUEUE_ENTERED)
        val totalGranted = queueEventLogRepository.countByEventIdAndType(eventId, QueueEventType.ENTRY_GRANTED)
        val totalVerified = queueEventLogRepository.countByEventIdAndType(eventId, QueueEventType.ENTRY_VERIFIED)
        val totalLeft = queueEventLogRepository.countByEventIdAndType(eventId, QueueEventType.QUEUE_LEFT)
        val totalBotBlocked = queueEventLogRepository.countByEventIdAndType(eventId, QueueEventType.BOT_BLOCKED)

        val enteredLastMinute = queueEventLogRepository.countByEventIdAndTypeSince(eventId, QueueEventType.QUEUE_ENTERED, oneMinuteAgo)
        val grantedLastMinute = queueEventLogRepository.countByEventIdAndTypeSince(eventId, QueueEventType.ENTRY_GRANTED, oneMinuteAgo)
        val enteredLastHour = queueEventLogRepository.countByEventIdAndTypeSince(eventId, QueueEventType.QUEUE_ENTERED, oneHourAgo)

        val abandonRate = if (totalEntered > 0) totalLeft.toDouble() / totalEntered * 100 else 0.0
        val conversionRate = if (totalGranted > 0) totalVerified.toDouble() / totalGranted * 100 else 0.0

        return ApiResponse.ok(EventStatsResponse(
            eventId = eventId,
            totals = TotalStats(
                entered = totalEntered,
                granted = totalGranted,
                verified = totalVerified,
                left = totalLeft,
                botBlocked = totalBotBlocked
            ),
            rates = RateStats(
                enteredPerMinute = enteredLastMinute,
                grantedPerMinute = grantedLastMinute,
                enteredPerHour = enteredLastHour
            ),
            percentages = PercentageStats(
                abandonRate = "%.1f".format(abandonRate),
                conversionRate = "%.1f".format(conversionRate)
            )
        ))
    }

    @GetMapping("/{eventId}/logs")
    suspend fun getEventLogs(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID,
        @RequestParam(defaultValue = "50") limit: Int
    ): ApiResponse<List<EventLogResponse>> {
        val tid = UUID.fromString(tenantId)
        eventService.findById(eventId, tid)

        val logs = queueEventLogRepository.findByEventId(eventId, limit.coerceAtMost(200))
        return ApiResponse.ok(logs.map { log ->
            EventLogResponse(
                id = log.id,
                eventType = log.eventType.name,
                userId = log.userId,
                payload = log.payload,
                createdAt = log.createdAt
            )
        })
    }
}

data class EventStatsResponse(
    val eventId: UUID,
    val totals: TotalStats,
    val rates: RateStats,
    val percentages: PercentageStats
)

data class TotalStats(
    val entered: Long,
    val granted: Long,
    val verified: Long,
    val left: Long,
    val botBlocked: Long
)

data class RateStats(
    val enteredPerMinute: Long,
    val grantedPerMinute: Long,
    val enteredPerHour: Long
)

data class PercentageStats(
    val abandonRate: String,
    val conversionRate: String
)

data class EventLogResponse(
    val id: Long?,
    val eventType: String,
    val userId: String?,
    val payload: Map<String, Any?>,
    val createdAt: Instant
)
