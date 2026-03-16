package com.pulseq.core.port

import com.pulseq.core.domain.QueueEntry
import java.util.UUID

interface QueueEntryRepository {
    suspend fun save(entry: QueueEntry): QueueEntry
    suspend fun findByEventIdAndUserId(eventId: UUID, userId: String): QueueEntry?
    suspend fun findByQueueTicket(queueTicket: String): QueueEntry?
    suspend fun update(entry: QueueEntry): QueueEntry
    suspend fun countByEventIdAndStatus(eventId: UUID, status: String): Long
}
