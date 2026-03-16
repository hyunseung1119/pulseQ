package com.pulseq.infra.persistence.repository

import com.pulseq.core.domain.QueueStatus
import com.pulseq.infra.persistence.entity.QueueEntryEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface JpaQueueEntryRepository : JpaRepository<QueueEntryEntity, UUID> {
    fun findByEventIdAndUserId(eventId: UUID, userId: String): QueueEntryEntity?
    fun findByQueueTicket(queueTicket: String): QueueEntryEntity?
    fun countByEventIdAndStatus(eventId: UUID, status: QueueStatus): Long
}
