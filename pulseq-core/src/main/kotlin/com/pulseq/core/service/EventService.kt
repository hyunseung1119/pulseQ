package com.pulseq.core.service

import com.pulseq.core.domain.Event
import com.pulseq.core.domain.EventStatus
import com.pulseq.core.exception.EventNotFoundException
import com.pulseq.core.port.EventRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class EventService(
    private val eventRepository: EventRepository
) {
    suspend fun create(
        tenantId: UUID,
        name: String,
        slug: String,
        maxCapacity: Int,
        startAt: Instant,
        endAt: Instant,
        rateLimit: Int = 100,
        entryTokenTtlSeconds: Int = 300,
        botDetectionEnabled: Boolean = true,
        botScoreThreshold: Double = 0.80,
        webhookUrl: String? = null
    ): Event {
        val event = Event(
            tenantId = tenantId,
            name = name,
            slug = slug,
            maxCapacity = maxCapacity,
            startAt = startAt,
            endAt = endAt,
            rateLimit = rateLimit,
            entryTokenTtlSeconds = entryTokenTtlSeconds,
            botDetectionEnabled = botDetectionEnabled,
            botScoreThreshold = botScoreThreshold,
            webhookUrl = webhookUrl
        )
        return eventRepository.save(event)
    }

    suspend fun findById(eventId: UUID, tenantId: UUID): Event {
        val event = eventRepository.findById(eventId)
            ?: throw EventNotFoundException(eventId.toString())
        if (event.tenantId != tenantId) {
            throw EventNotFoundException(eventId.toString())
        }
        return event
    }

    suspend fun findByTenantId(tenantId: UUID, status: EventStatus?, page: Int, size: Int): List<Event> {
        return if (status != null) {
            eventRepository.findByTenantIdAndStatus(tenantId, status)
        } else {
            eventRepository.findByTenantId(tenantId, page, size)
        }
    }

    suspend fun update(
        eventId: UUID,
        tenantId: UUID,
        name: String? = null,
        maxCapacity: Int? = null,
        rateLimit: Int? = null,
        entryTokenTtlSeconds: Int? = null,
        botDetectionEnabled: Boolean? = null,
        botScoreThreshold: Double? = null,
        webhookUrl: String? = null
    ): Event {
        val existing = findById(eventId, tenantId)
        val updated = existing.copy(
            name = name ?: existing.name,
            maxCapacity = maxCapacity ?: existing.maxCapacity,
            rateLimit = rateLimit ?: existing.rateLimit,
            entryTokenTtlSeconds = entryTokenTtlSeconds ?: existing.entryTokenTtlSeconds,
            botDetectionEnabled = botDetectionEnabled ?: existing.botDetectionEnabled,
            botScoreThreshold = botScoreThreshold ?: existing.botScoreThreshold,
            webhookUrl = webhookUrl ?: existing.webhookUrl,
            updatedAt = Instant.now()
        )
        return eventRepository.update(updated)
    }

    suspend fun delete(eventId: UUID, tenantId: UUID) {
        findById(eventId, tenantId)
        eventRepository.deleteById(eventId)
    }

    suspend fun countByTenantId(tenantId: UUID): Long =
        eventRepository.countByTenantId(tenantId)
}
