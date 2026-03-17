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

/**
 * Kafka 이벤트 컨슈머 — queue-events 토픽을 소비하여 PostgreSQL에 영구 저장한다.
 * 3개 동시 소비 스레드(concurrency=3)로 처리량을 높인다.
 * 이벤트 로그는 통계 조회 및 대시보드 실시간 로그에 사용된다.
 */
@Component
class KafkaQueueEventConsumer(
    private val queueEventLogRepository: QueueEventLogRepository,  // 이벤트 로그 저장소
    private val objectMapper: ObjectMapper                         // JSON 역직렬화
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 이벤트 소비 및 DB 저장.
     * @KafkaListener가 Kafka 컨슈머 그룹을 자동 관리.
     * runBlocking: Kafka 리스너는 일반 함수이므로 코루틴 브릿지 필요.
     */
    @KafkaListener(
        topics = [KafkaConfig.TOPIC_QUEUE_EVENTS],  // 소비 토픽
        groupId = "pulseq-event-persister",         // 컨슈머 그룹 ID
        concurrency = "3"                           // 3개 스레드로 병렬 소비
    )
    fun consume(message: String) = runBlocking {
        try {
            val event = parseEvent(message)       // JSON → QueueEventLog 파싱
            queueEventLogRepository.save(event)   // PostgreSQL에 저장 (BRIN 인덱스)
            log.debug("Persisted event: type={}, eventId={}", event.eventType, event.eventId)
        } catch (e: Exception) {
            // 파싱/저장 실패 시 로그만 남김 — DLQ 토픽으로 보내는 것이 이상적
            log.error("Failed to consume event: {}", e.message, e)
        }
    }

    /**
     * JSON 메시지를 QueueEventLog 도메인 객체로 파싱.
     * Kafka 메시지는 문자열이므로 수동 역직렬화 필요.
     */
    private fun parseEvent(message: String): QueueEventLog {
        val map: Map<String, Any?> = objectMapper.readValue(message)
        return QueueEventLog(
            eventId = UUID.fromString(map["eventId"] as String),
            userId = map["userId"] as? String,
            eventType = QueueEventType.valueOf(map["eventType"] as String),
            // payload를 다시 Map<String, Any?>로 역직렬화 (JSONB 컬럼에 저장)
            payload = objectMapper.readValue(
                objectMapper.writeValueAsString(map["payload"] ?: emptyMap<String, Any?>())
            ),
            createdAt = map["createdAt"]?.let { Instant.parse(it as String) } ?: Instant.now()
        )
    }
}
