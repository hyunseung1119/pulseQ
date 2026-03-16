package com.pulseq.infra.persistence.entity

import com.pulseq.core.domain.QueueEventLog
import com.pulseq.core.domain.QueueEventType
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "queue_event_log")
class QueueEventLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "event_id", nullable = false)
    val eventId: UUID = UUID.randomUUID(),

    @Column(name = "user_id")
    val userId: String? = null,

    @Column(name = "event_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    val eventType: QueueEventType = QueueEventType.QUEUE_ENTERED,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    val payload: Map<String, Any?> = emptyMap(),

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now()
) {
    fun toDomain(): QueueEventLog = QueueEventLog(
        id = id,
        eventId = eventId,
        userId = userId,
        eventType = eventType,
        payload = payload,
        createdAt = createdAt
    )

    companion object {
        fun from(domain: QueueEventLog): QueueEventLogEntity = QueueEventLogEntity(
            id = domain.id,
            eventId = domain.eventId,
            userId = domain.userId,
            eventType = domain.eventType,
            payload = domain.payload,
            createdAt = domain.createdAt
        )
    }
}
