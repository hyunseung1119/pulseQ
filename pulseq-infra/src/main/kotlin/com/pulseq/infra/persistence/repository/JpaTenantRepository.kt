package com.pulseq.infra.persistence.repository

import com.pulseq.infra.persistence.entity.TenantEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JpaTenantRepository : JpaRepository<TenantEntity, UUID> {
    fun findByEmail(email: String): TenantEntity?
    fun findByApiKeyHash(apiKeyHash: String): TenantEntity?
    fun existsByEmail(email: String): Boolean
}
