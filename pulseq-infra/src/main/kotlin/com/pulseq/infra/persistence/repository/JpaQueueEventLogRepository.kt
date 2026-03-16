package com.pulseq.infra.persistence.repository

import com.pulseq.core.domain.QueueEventType
import com.pulseq.infra.persistence.entity.QueueEventLogEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
interface JpaQueueEventLogRepository : JpaRepository<QueueEventLogEntity, Long> {

    fun findByEventIdOrderByCreatedAtDesc(eventId: UUID, pageable: Pageable): List<QueueEventLogEntity>

    fun countByEventIdAndEventType(eventId: UUID, eventType: QueueEventType): Long

    @Query("SELECT COUNT(e) FROM QueueEventLogEntity e WHERE e.eventId = :eventId AND e.eventType = :eventType AND e.createdAt >= :since")
    fun countByEventIdAndEventTypeAndCreatedAtAfter(
        eventId: UUID,
        eventType: QueueEventType,
        since: Instant
    ): Long
}
