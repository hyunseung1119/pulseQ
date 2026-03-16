package com.pulseq.core.port

import com.pulseq.core.domain.Tenant
import java.util.UUID

interface TenantRepository {
    suspend fun save(tenant: Tenant): Tenant
    suspend fun findById(id: UUID): Tenant?
    suspend fun findByEmail(email: String): Tenant?
    suspend fun findByApiKeyHash(apiKeyHash: String): Tenant?
    suspend fun existsByEmail(email: String): Boolean
    suspend fun update(tenant: Tenant): Tenant
}
