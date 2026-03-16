package com.pulseq.infra.persistence.adapter

import com.pulseq.core.domain.Tenant
import com.pulseq.core.port.TenantRepository
import com.pulseq.infra.persistence.entity.TenantEntity
import com.pulseq.infra.persistence.repository.JpaTenantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class TenantRepositoryAdapter(
    private val jpaTenantRepository: JpaTenantRepository
) : TenantRepository {

    override suspend fun save(tenant: Tenant): Tenant = withContext(Dispatchers.IO) {
        jpaTenantRepository.save(TenantEntity.from(tenant)).toDomain()
    }

    override suspend fun findById(id: UUID): Tenant? = withContext(Dispatchers.IO) {
        jpaTenantRepository.findById(id).orElse(null)?.toDomain()
    }

    override suspend fun findByEmail(email: String): Tenant? = withContext(Dispatchers.IO) {
        jpaTenantRepository.findByEmail(email)?.toDomain()
    }

    override suspend fun findByApiKeyHash(apiKeyHash: String): Tenant? = withContext(Dispatchers.IO) {
        jpaTenantRepository.findByApiKeyHash(apiKeyHash)?.toDomain()
    }

    override suspend fun existsByEmail(email: String): Boolean = withContext(Dispatchers.IO) {
        jpaTenantRepository.existsByEmail(email)
    }

    override suspend fun update(tenant: Tenant): Tenant = withContext(Dispatchers.IO) {
        jpaTenantRepository.save(TenantEntity.from(tenant)).toDomain()
    }
}
