package com.pulseq.infra.persistence.adapter

import com.pulseq.core.domain.Event
import com.pulseq.core.domain.EventStatus
import com.pulseq.core.port.EventRepository
import com.pulseq.infra.persistence.entity.EventEntity
import com.pulseq.infra.persistence.repository.JpaEventRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class EventRepositoryAdapter(
    private val jpaEventRepository: JpaEventRepository
) : EventRepository {

    override suspend fun save(event: Event): Event = withContext(Dispatchers.IO) {
        jpaEventRepository.save(EventEntity.from(event)).toDomain()
    }

    override suspend fun findById(id: UUID): Event? = withContext(Dispatchers.IO) {
        jpaEventRepository.findById(id).orElse(null)?.toDomain()
    }

    override suspend fun findByTenantId(tenantId: UUID, page: Int, size: Int): List<Event> =
        withContext(Dispatchers.IO) {
            val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
            jpaEventRepository.findByTenantId(tenantId, pageable).map { it.toDomain() }
        }

    override suspend fun findByTenantIdAndSlug(tenantId: UUID, slug: String): Event? =
        withContext(Dispatchers.IO) {
            jpaEventRepository.findByTenantIdAndSlug(tenantId, slug)?.toDomain()
        }

    override suspend fun findByTenantIdAndStatus(tenantId: UUID, status: EventStatus): List<Event> =
        withContext(Dispatchers.IO) {
            jpaEventRepository.findByTenantIdAndStatus(tenantId, status).map { it.toDomain() }
        }

    override suspend fun update(event: Event): Event = withContext(Dispatchers.IO) {
        jpaEventRepository.save(EventEntity.from(event)).toDomain()
    }

    override suspend fun deleteById(id: UUID): Unit = withContext(Dispatchers.IO) {
        jpaEventRepository.deleteById(id)
    }

    override suspend fun countByTenantId(tenantId: UUID): Long = withContext(Dispatchers.IO) {
        jpaEventRepository.countByTenantId(tenantId)
    }
}
