package com.pulseq.core.port

import com.pulseq.core.domain.QueueEventLog

interface EventPublisher {
    suspend fun publish(event: QueueEventLog)
    suspend fun publishBatch(events: List<QueueEventLog>)
}
