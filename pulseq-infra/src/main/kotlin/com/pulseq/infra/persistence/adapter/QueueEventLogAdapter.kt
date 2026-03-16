package com.pulseq.infra.persistence.adapter

import com.pulseq.core.domain.QueueEventLog
import com.pulseq.core.domain.QueueEventType
import com.pulseq.core.port.QueueEventLogRepository
import com.pulseq.infra.persistence.entity.QueueEventLogEntity
import com.pulseq.infra.persistence.repository.JpaQueueEventLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class QueueEventLogAdapter(
    private val jpaRepository: JpaQueueEventLogRepository
) : QueueEventLogRepository {

    override suspend fun save(log: QueueEventLog): QueueEventLog = withContext(Dispatchers.IO) {
        jpaRepository.save(QueueEventLogEntity.from(log)).toDomain()
    }

    override suspend fun saveBatch(logs: List<QueueEventLog>) = withContext(Dispatchers.IO) {
        jpaRepository.saveAll(logs.map { QueueEventLogEntity.from(it) })
        Unit
    }

    override suspend fun findByEventId(eventId: UUID, limit: Int): List<QueueEventLog> =
        withContext(Dispatchers.IO) {
            jpaRepository.findByEventIdOrderByCreatedAtDesc(eventId, PageRequest.of(0, limit))
                .map { it.toDomain() }
        }

    override suspend fun countByEventIdAndType(eventId: UUID, type: QueueEventType): Long =
        withContext(Dispatchers.IO) {
            jpaRepository.countByEventIdAndEventType(eventId, type)
        }

    override suspend fun countByEventIdAndTypeSince(eventId: UUID, type: QueueEventType, since: Instant): Long =
        withContext(Dispatchers.IO) {
            jpaRepository.countByEventIdAndEventTypeAndCreatedAtAfter(eventId, type, since)
        }
}
