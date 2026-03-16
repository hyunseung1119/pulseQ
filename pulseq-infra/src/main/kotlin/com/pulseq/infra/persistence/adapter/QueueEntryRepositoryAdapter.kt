package com.pulseq.infra.persistence.adapter

import com.pulseq.core.domain.QueueEntry
import com.pulseq.core.domain.QueueStatus
import com.pulseq.core.port.QueueEntryRepository
import com.pulseq.infra.persistence.entity.QueueEntryEntity
import com.pulseq.infra.persistence.repository.JpaQueueEntryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class QueueEntryRepositoryAdapter(
    private val jpaQueueEntryRepository: JpaQueueEntryRepository
) : QueueEntryRepository {

    override suspend fun save(entry: QueueEntry): QueueEntry = withContext(Dispatchers.IO) {
        jpaQueueEntryRepository.save(QueueEntryEntity.from(entry)).toDomain()
    }

    override suspend fun findByEventIdAndUserId(eventId: UUID, userId: String): QueueEntry? =
        withContext(Dispatchers.IO) {
            jpaQueueEntryRepository.findByEventIdAndUserId(eventId, userId)?.toDomain()
        }

    override suspend fun findByQueueTicket(queueTicket: String): QueueEntry? =
        withContext(Dispatchers.IO) {
            jpaQueueEntryRepository.findByQueueTicket(queueTicket)?.toDomain()
        }

    override suspend fun update(entry: QueueEntry): QueueEntry = withContext(Dispatchers.IO) {
        jpaQueueEntryRepository.save(QueueEntryEntity.from(entry)).toDomain()
    }

    override suspend fun countByEventIdAndStatus(eventId: UUID, status: String): Long =
        withContext(Dispatchers.IO) {
            jpaQueueEntryRepository.countByEventIdAndStatus(eventId, QueueStatus.valueOf(status))
        }
}
