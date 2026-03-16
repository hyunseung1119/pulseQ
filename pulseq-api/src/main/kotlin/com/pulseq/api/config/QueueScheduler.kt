package com.pulseq.api.config

import com.pulseq.core.domain.EventStatus
import com.pulseq.core.port.EventRepository
import com.pulseq.core.service.QueueService
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 대기열 처리 스케줄러.
 * 매 1초마다 ACTIVE 이벤트의 대기열에서 rateLimit만큼 사용자를 pop하고
 * 입장 토큰을 발급한다.
 */
@Component
@EnableScheduling
class QueueScheduler(
    private val eventRepository: EventRepository,
    private val queueService: QueueService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 1000)
    fun processActiveQueues() = runBlocking {
        try {
            val activeEvents = eventRepository.findAllByStatus(EventStatus.ACTIVE)
            for (event in activeEvents) {
                try {
                    val tokens = queueService.processQueue(event.id)
                    if (tokens.isNotEmpty()) {
                        log.debug("Processed {} entries for event {}", tokens.size, event.id)
                    }
                } catch (e: Exception) {
                    log.error("Failed to process queue for event {}: {}", event.id, e.message)
                }
            }
        } catch (e: Exception) {
            log.error("Queue scheduler error", e)
        }
    }
}
