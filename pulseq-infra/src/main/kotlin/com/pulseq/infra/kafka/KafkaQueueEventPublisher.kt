package com.pulseq.infra.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import com.pulseq.core.domain.QueueEventLog
import com.pulseq.core.port.EventPublisher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * Kafka 이벤트 발행 어댑터 — EventPublisher 포트의 구현체.
 * 대기열 이벤트(입장/퇴장/봇차단/입장허가 등)를 Kafka 토픽으로 발행한다.
 * key = eventId로 파티셔닝 → 같은 이벤트의 로그가 순서대로 처리됨.
 */
@Component
class KafkaQueueEventPublisher(
    private val kafkaTemplate: KafkaTemplate<String, String>,  // Spring Kafka 프로듀서
    private val objectMapper: ObjectMapper                     // JSON 직렬화
) : EventPublisher {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 단건 이벤트 발행.
     * Dispatchers.IO에서 실행하여 코루틴 이벤트 루프 블로킹 방지.
     * 발행 실패 시 로그만 남기고 예외를 삼키지 않음 (비동기 발행이므로 재시도 불필요).
     */
    override suspend fun publish(event: QueueEventLog) {
        withContext(Dispatchers.IO) {
            try {
                val key = event.eventId.toString()  // 파티션 키 = eventId
                val value = objectMapper.writeValueAsString(event)  // 이벤트를 JSON으로 직렬화
                kafkaTemplate.send(KafkaConfig.TOPIC_QUEUE_EVENTS, key, value)
                log.debug("Published event: type={}, eventId={}, userId={}",
                    event.eventType, event.eventId, event.userId)
            } catch (e: Exception) {
                log.error("Failed to publish event: {}", e.message, e)
            }
        }
    }

    /**
     * 배치 이벤트 발행 — processQueue에서 여러 유저에게 동시에 ENTRY_GRANTED 발행 시 사용.
     * 각 이벤트를 개별 전송 (Kafka 프로듀서 내부에서 배치 처리됨).
     */
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
