package com.pulseq.core.service

import com.pulseq.core.domain.QueueEventLog
import com.pulseq.core.domain.QueueEventType
import com.pulseq.core.port.BotDetectionPort
import com.pulseq.core.port.BotScoreResult
import com.pulseq.core.port.EventPublisher
import com.pulseq.core.port.QueuePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class BotDetectionService(
    private val botDetectionPort: BotDetectionPort,
    private val queuePort: QueuePort,
    private val eventPublisher: EventPublisher
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 봇 스코어를 확인한다.
     * 1. Redis 캐시 확인 (sub-ms)
     * 2. 캐시 미스 → ML 서비스 호출
     * 3. ML 서비스 장애 → Rule-based fallback
     */
    suspend fun checkBotScore(
        eventId: UUID,
        userId: String,
        ipAddress: String?,
        userAgent: String?,
        fingerprint: String?,
        botScoreThreshold: Double
    ): BotScoreResult {
        // 1. Redis 캐시 확인
        val cachedScore = queuePort.getBotScore(eventId, userId)
        if (cachedScore != null) {
            return BotScoreResult(
                userId = userId,
                botScore = cachedScore,
                isBot = cachedScore >= botScoreThreshold
            )
        }

        // 2. ML 서비스 호출 시도
        return try {
            if (!botDetectionPort.isAvailable()) {
                return applyRules(eventId, userId, ipAddress, userAgent, fingerprint, botScoreThreshold)
            }

            val features = buildFeatures(ipAddress, userAgent, fingerprint)
            val result = botDetectionPort.score(eventId, userId, features)

            // Redis에 캐싱 (TTL 10분)
            queuePort.setBotScore(eventId, userId, result.botScore, 600)

            if (result.isBot) {
                publishBotEvent(eventId, userId, result, QueueEventType.BOT_DETECTED)
            }

            result
        } catch (e: Exception) {
            log.warn("ML service call failed for user {}: {}", userId, e.message)
            applyRules(eventId, userId, ipAddress, userAgent, fingerprint, botScoreThreshold)
        }
    }

    /**
     * Rule-based fallback — ML 서비스 장애 시 최소한의 봇 차단
     */
    private suspend fun applyRules(
        eventId: UUID,
        userId: String,
        ipAddress: String?,
        userAgent: String?,
        fingerprint: String?,
        threshold: Double
    ): BotScoreResult {
        var score = 0.0
        val reasons = mutableListOf<String>()

        // Rule 1: 헤드리스 브라우저 검사
        val ua = userAgent?.lowercase() ?: ""
        if (ua.contains("headless") || ua.contains("phantomjs") || ua.contains("selenium")) {
            score += 0.4
            reasons.add("headless_browser")
        }

        // Rule 2: 알려진 봇 UA
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")) {
            score += 0.3
            reasons.add("known_bot_ua")
        }

        // Rule 3: UA 없음
        if (userAgent.isNullOrBlank()) {
            score += 0.2
            reasons.add("missing_ua")
        }

        val isBot = score >= threshold
        val result = BotScoreResult(userId = userId, botScore = score, isBot = isBot)

        if (isBot) {
            publishBotEvent(eventId, userId, result, QueueEventType.BOT_DETECTED)
        }

        return result
    }

    private suspend fun publishBotEvent(
        eventId: UUID,
        userId: String,
        result: BotScoreResult,
        type: QueueEventType
    ) {
        eventPublisher.publish(QueueEventLog(
            eventId = eventId,
            userId = userId,
            eventType = type,
            payload = mapOf(
                "botScore" to result.botScore,
                "isBot" to result.isBot,
                "reasons" to result.topReasons.map { it.feature }
            )
        ))
    }

    private fun buildFeatures(
        ipAddress: String?,
        userAgent: String?,
        fingerprint: String?
    ): Map<String, Any?> {
        val ua = userAgent?.lowercase() ?: ""
        val isHeadless = ua.contains("headless") || ua.contains("phantomjs") || ua.contains("selenium")
        val isBotUa = ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")

        return mapOf(
            "ua_is_headless" to isHeadless,
            "is_datacenter_ip" to false,
            "is_vpn_tor" to false,
            "has_cookie" to userAgent?.isNotBlank(),
            "mouse_movement_entropy" to if (isHeadless) 0.1 else 5.0,
            "click_interval_mean" to if (isHeadless || isBotUa) 80.0 else 2000.0,
            "click_interval_std" to if (isHeadless || isBotUa) 5.0 else 500.0,
            "click_interval_min" to if (isHeadless || isBotUa) 50.0 else 800.0,
            "exact_interval_ratio" to if (isHeadless || isBotUa) 0.85 else 0.05,
            "request_count_1m" to if (isHeadless || isBotUa) 50 else 5,
            "request_count_5m" to if (isHeadless || isBotUa) 200 else 15,
            "ip_request_count" to 1,
            "ip_user_count" to 1,
            "fingerprint_collision" to 0,
            "scroll_events" to if (isHeadless) 0 else 5,
            "page_dwell_time" to if (isHeadless) 0.5 else 30.0,
            "time_before_event" to 60.0
        )
    }
}
