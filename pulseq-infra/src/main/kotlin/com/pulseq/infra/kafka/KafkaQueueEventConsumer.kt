package com.pulseq.infra.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.pulseq.core.domain.QueueEventLog
import com.pulseq.core.domain.QueueEventType
import com.pulseq.core.port.QueueEventLogRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class KafkaQueueEventConsumer(
    private val queueEventLogRepository: QueueEventLogRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = [KafkaConfig.TOPIC_QUEUE_EVENTS],
        groupId = "pulseq-event-persister",
        concurrency = "3"
    )
    fun consume(message: String) = runBlocking {
        try {
            val event = parseEvent(message)
            queueEventLogRepository.save(event)
            log.debug("Persisted event: type={}, eventId={}", event.eventType, event.eventId)
        } catch (e: Exception) {
            log.error("Failed to consume event: {}", e.message, e)
        }
    }

    private fun parseEvent(message: String): QueueEventLog {
        val map: Map<String, Any?> = objectMapper.readValue(message)
        return QueueEventLog(
            eventId = UUID.fromString(map["eventId"] as String),
            userId = map["userId"] as? String,
            eventType = QueueEventType.valueOf(map["eventType"] as String),
            payload = objectMapper.readValue(
                objectMapper.writeValueAsString(map["payload"] ?: emptyMap<String, Any?>())
            ),
            createdAt = map["createdAt"]?.let { Instant.parse(it as String) } ?: Instant.now()
        )
    }
}
