package com.pulseq.infra.persistence.repository

import com.pulseq.core.domain.EventStatus
import com.pulseq.infra.persistence.entity.EventEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JpaEventRepository : JpaRepository<EventEntity, UUID> {
    fun findByTenantId(tenantId: UUID, pageable: Pageable): List<EventEntity>
    fun findByTenantIdAndSlug(tenantId: UUID, slug: String): EventEntity?
    fun findByTenantIdAndStatus(tenantId: UUID, status: EventStatus): List<EventEntity>
    fun countByTenantId(tenantId: UUID): Long
}
