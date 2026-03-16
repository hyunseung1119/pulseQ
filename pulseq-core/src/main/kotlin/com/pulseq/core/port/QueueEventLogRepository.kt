package com.pulseq.core.port

import com.pulseq.core.domain.QueueEventLog
import com.pulseq.core.domain.QueueEventType
import java.time.Instant
import java.util.UUID

interface QueueEventLogRepository {
    suspend fun save(log: QueueEventLog): QueueEventLog
    suspend fun saveBatch(logs: List<QueueEventLog>)
    suspend fun findByEventId(eventId: UUID, limit: Int = 100): List<QueueEventLog>
    suspend fun countByEventIdAndType(eventId: UUID, type: QueueEventType): Long
    suspend fun countByEventIdAndTypeSince(eventId: UUID, type: QueueEventType, since: Instant): Long
}
