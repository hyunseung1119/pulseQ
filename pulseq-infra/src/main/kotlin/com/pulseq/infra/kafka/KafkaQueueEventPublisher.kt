package com.pulseq.infra.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.pulseq.core.domain.QueueEventLog
import com.pulseq.core.port.EventPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class KafkaQueueEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) : EventPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun publish(event: QueueEventLog) {
        withContext(Dispatchers.IO) {
            try {
                val key = event.eventId.toString()
                val value = objectMapper.writeValueAsString(event)
                kafkaTemplate.send(KafkaConfig.TOPIC_QUEUE_EVENTS, key, value)
                log.debug("Published event: type={}, eventId={}, userId={}",
                    event.eventType, event.eventId, event.userId)
            } catch (e: Exception) {
                log.error("Failed to publish event: {}", e.message, e)
            }
        }
    }

    override suspend fun publishBatch(events: List<QueueEventLog>) {
        withContext(Dispatchers.IO) {
            events.forEach { event ->
                try {
                    val key = event.eventId.toString()
                    val value = objectMapper.writeValueAsString(event)
                    kafkaTemplate.send(KafkaConfig.TOPIC_QUEUE_EVENTS, key, value)
                } catch (e: Exception) {
                    log.error("Failed to publish batch event: {}", e.message, e)
                }
            }
            log.debug("Published batch of {} events", events.size)
        }
    }
}
