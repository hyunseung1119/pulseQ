package com.pulseq.core.port

import com.pulseq.core.domain.Event
import com.pulseq.core.domain.EventStatus
import java.util.UUID

interface EventRepository {
    suspend fun save(event: Event): Event
    suspend fun findById(id: UUID): Event?
    suspend fun findByTenantId(tenantId: UUID, page: Int = 0, size: Int = 20): List<Event>
    suspend fun findByTenantIdAndSlug(tenantId: UUID, slug: String): Event?
    suspend fun findByTenantIdAndStatus(tenantId: UUID, status: EventStatus): List<Event>
    suspend fun update(event: Event): Event
    suspend fun deleteById(id: UUID)
    suspend fun countByTenantId(tenantId: UUID): Long
    suspend fun findAllByStatus(status: EventStatus): List<Event>

    /** 원자적 카운터 증가 — SQL UPDATE SET col = col + 1 (lost update 방지) */
    suspend fun incrementTotalEntered(eventId: UUID)
    suspend fun incrementTotalProcessed(eventId: UUID, count: Int)
    suspend fun incrementTotalAbandoned(eventId: UUID)
    suspend fun incrementTotalBotBlocked(eventId: UUID)
}
