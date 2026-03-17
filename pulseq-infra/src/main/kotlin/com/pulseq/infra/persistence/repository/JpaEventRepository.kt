package com.pulseq.infra.persistence.repository

import com.pulseq.core.domain.EventStatus
import com.pulseq.infra.persistence.entity.EventEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface JpaEventRepository : JpaRepository<EventEntity, UUID> {
    fun findByTenantId(tenantId: UUID, pageable: Pageable): List<EventEntity>
    fun findByTenantIdAndSlug(tenantId: UUID, slug: String): EventEntity?
    fun findByTenantIdAndStatus(tenantId: UUID, status: EventStatus): List<EventEntity>
    fun countByTenantId(tenantId: UUID): Long
    fun findByStatus(status: EventStatus): List<EventEntity>

    /** 원자적 카운터 증가 — DB 레벨에서 col = col + 1 (lost update 방지) */
    @Modifying
    @Query("UPDATE EventEntity e SET e.totalEntered = e.totalEntered + 1, e.updatedAt = CURRENT_TIMESTAMP WHERE e.id = :eventId")
    fun incrementTotalEntered(@Param("eventId") eventId: UUID)

    @Modifying
    @Query("UPDATE EventEntity e SET e.totalProcessed = e.totalProcessed + :count, e.updatedAt = CURRENT_TIMESTAMP WHERE e.id = :eventId")
    fun incrementTotalProcessed(@Param("eventId") eventId: UUID, @Param("count") count: Int)

    @Modifying
    @Query("UPDATE EventEntity e SET e.totalAbandoned = e.totalAbandoned + 1, e.updatedAt = CURRENT_TIMESTAMP WHERE e.id = :eventId")
    fun incrementTotalAbandoned(@Param("eventId") eventId: UUID)

    @Modifying
    @Query("UPDATE EventEntity e SET e.totalBotBlocked = e.totalBotBlocked + 1, e.updatedAt = CURRENT_TIMESTAMP WHERE e.id = :eventId")
    fun incrementTotalBotBlocked(@Param("eventId") eventId: UUID)
}
