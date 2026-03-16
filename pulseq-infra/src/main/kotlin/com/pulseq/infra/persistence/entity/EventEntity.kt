package com.pulseq.infra.persistence.entity

import com.pulseq.core.domain.Event
import com.pulseq.core.domain.EventStatus
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "events")
class EventEntity(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "tenant_id", nullable = false)
    val tenantId: UUID,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val slug: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: EventStatus = EventStatus.SCHEDULED,

    @Column(name = "max_capacity", nullable = false)
    val maxCapacity: Int,

    @Column(name = "rate_limit", nullable = false)
    val rateLimit: Int = 100,

    @Column(name = "entry_token_ttl_seconds", nullable = false)
    val entryTokenTtlSeconds: Int = 300,

    @Column(name = "start_at", nullable = false)
    val startAt: Instant,

    @Column(name = "end_at", nullable = false)
    val endAt: Instant,

    @Column(name = "bot_detection_enabled", nullable = false)
    val botDetectionEnabled: Boolean = true,

    @Column(name = "bot_score_threshold", nullable = false, precision = 3, scale = 2)
    val botScoreThreshold: BigDecimal = BigDecimal("0.80"),

    @Column(name = "webhook_url")
    val webhookUrl: String? = null,

    @Column(name = "total_entered", nullable = false)
    val totalEntered: Int = 0,

    @Column(name = "total_processed", nullable = false)
    val totalProcessed: Int = 0,

    @Column(name = "total_abandoned", nullable = false)
    val totalAbandoned: Int = 0,

    @Column(name = "total_bot_blocked", nullable = false)
    val totalBotBlocked: Int = 0,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
) {
    fun toDomain(): Event = Event(
        id = id,
        tenantId = tenantId,
        name = name,
        slug = slug,
        status = status,
        maxCapacity = maxCapacity,
        rateLimit = rateLimit,
        entryTokenTtlSeconds = entryTokenTtlSeconds,
        startAt = startAt,
        endAt = endAt,
        botDetectionEnabled = botDetectionEnabled,
        botScoreThreshold = botScoreThreshold.toDouble(),
        webhookUrl = webhookUrl,
        totalEntered = totalEntered,
        totalProcessed = totalProcessed,
        totalAbandoned = totalAbandoned,
        totalBotBlocked = totalBotBlocked,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        fun from(event: Event): EventEntity = EventEntity(
            id = event.id,
            tenantId = event.tenantId,
            name = event.name,
            slug = event.slug,
            status = event.status,
            maxCapacity = event.maxCapacity,
            rateLimit = event.rateLimit,
            entryTokenTtlSeconds = event.entryTokenTtlSeconds,
            startAt = event.startAt,
            endAt = event.endAt,
            botDetectionEnabled = event.botDetectionEnabled,
            botScoreThreshold = BigDecimal.valueOf(event.botScoreThreshold),
            webhookUrl = event.webhookUrl,
            totalEntered = event.totalEntered,
            totalProcessed = event.totalProcessed,
            totalAbandoned = event.totalAbandoned,
            totalBotBlocked = event.totalBotBlocked,
            createdAt = event.createdAt,
            updatedAt = event.updatedAt
        )
    }
}
