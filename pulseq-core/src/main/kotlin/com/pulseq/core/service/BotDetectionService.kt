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

/**
 * 봇 탐지 서비스 — 3단계 fallback 전략으로 봇을 판별한다.
 * 1단계: Redis 캐시 확인 (sub-ms)
 * 2단계: ML 서비스 호출 (FastAPI + LightGBM, ~50ms)
 * 3단계: Rule 기반 fallback (ML 장애 시)
 */
@Service
class BotDetectionService(
    private val botDetectionPort: BotDetectionPort,  // ML 서비스 포트 (FastAPI 호출)
    private val queuePort: QueuePort,                // Redis 캐시 포트 (봇 스코어 캐싱)
    private val eventPublisher: EventPublisher       // Kafka 이벤트 발행 포트
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 봇 스코어를 확인한다.
     * @param botScoreThreshold 이벤트별 봇 판정 임계값 (기본 0.8)
     * @return BotScoreResult — isBot=true이면 봇으로 판정
     */
    suspend fun checkBotScore(
        eventId: UUID,
        userId: String,
        ipAddress: String?,
        userAgent: String?,
        fingerprint: String?,
        botScoreThreshold: Double
    ): BotScoreResult {
        // 1단계: Redis 캐시 확인 — 같은 유저의 최근 판정 결과 재사용
        val cachedScore = queuePort.getBotScore(eventId, userId)
        if (cachedScore != null) {
            return BotScoreResult(
                userId = userId,
                botScore = cachedScore,
                isBot = cachedScore >= botScoreThreshold
            )
        }

        // 2단계: ML 서비스 호출 시도
        return try {
            // ML 서비스 헬스체크 — 장애 시 바로 Rule fallback으로
            if (!botDetectionPort.isAvailable()) {
                return applyRules(eventId, userId, ipAddress, userAgent, fingerprint, botScoreThreshold)
            }

            // 17개 피처를 추출하여 ML 모델에 전달
            val features = buildFeatures(ipAddress, userAgent, fingerprint)
            val result = botDetectionPort.score(eventId, userId, features)

            // Redis에 결과 캐싱 (TTL 10분 = 600초)
            queuePort.setBotScore(eventId, userId, result.botScore, 600)

            // 봇 판정 시 Kafka 이벤트 발행
            if (result.isBot) {
                publishBotEvent(eventId, userId, result, QueueEventType.BOT_DETECTED)
            }

            result
        } catch (e: Exception) {
            // 3단계: ML 서비스 호출 실패 시 Rule 기반 fallback
            log.warn("ML service call failed for user {}: {}", userId, e.message)
            applyRules(eventId, userId, ipAddress, userAgent, fingerprint, botScoreThreshold)
        }
    }

    /**
     * Rule 기반 fallback — ML 서비스 장애 시 최소한의 봇 차단.
     * User-Agent 패턴으로 명백한 봇만 걸러낸다.
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

        // Rule 1: 헤드리스 브라우저 검사 (Selenium, PhantomJS 등)
        val ua = userAgent?.lowercase() ?: ""
        if (ua.contains("headless") || ua.contains("phantomjs") || ua.contains("selenium")) {
            score += 0.4  // 강한 봇 신호
            reasons.add("headless_browser")
        }

        // Rule 2: 알려진 봇 User-Agent 패턴
        if (ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")) {
            score += 0.3
            reasons.add("known_bot_ua")
        }

        // Rule 3: User-Agent 헤더 누락 (정상 브라우저는 항상 포함)
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

    /** Kafka로 봇 탐지 이벤트 발행 (모니터링/로깅용) */
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

    /**
     * ML 모델 입력 피처 구성 — 17개 피처를 User-Agent 기반으로 추정.
     * 프로덕션에서는 프론트엔드 SDK가 마우스/클릭/스크롤 데이터를 수집하여 전달.
     */
    private fun buildFeatures(
        ipAddress: String?,
        userAgent: String?,
        fingerprint: String?
    ): Map<String, Any?> {
        val ua = userAgent?.lowercase() ?: ""
        val isHeadless = ua.contains("headless") || ua.contains("phantomjs") || ua.contains("selenium")
        val isBotUa = ua.contains("bot") || ua.contains("crawler") || ua.contains("spider")

        return mapOf(
            "ua_is_headless" to isHeadless,             // 헤드리스 브라우저 여부
            "is_datacenter_ip" to false,                // 데이터센터 IP 여부 (향후 GeoIP DB 연동)
            "is_vpn_tor" to false,                      // VPN/Tor 사용 여부
            "has_cookie" to userAgent?.isNotBlank(),     // 쿠키 지원 여부
            "mouse_movement_entropy" to if (isHeadless) 0.1 else 5.0,     // 마우스 움직임 엔트로피 (봇은 낮음)
            "click_interval_mean" to if (isHeadless || isBotUa) 80.0 else 2000.0,   // 클릭 간격 평균(ms)
            "click_interval_std" to if (isHeadless || isBotUa) 5.0 else 500.0,      // 클릭 간격 표준편차 (봇은 일정)
            "click_interval_min" to if (isHeadless || isBotUa) 50.0 else 800.0,     // 클릭 최소 간격
            "exact_interval_ratio" to if (isHeadless || isBotUa) 0.85 else 0.05,    // 정확히 같은 간격 비율
            "request_count_1m" to if (isHeadless || isBotUa) 50 else 5,             // 1분 내 요청 수
            "request_count_5m" to if (isHeadless || isBotUa) 200 else 15,           // 5분 내 요청 수
            "ip_request_count" to 1,                    // 같은 IP의 총 요청 수
            "ip_user_count" to 1,                       // 같은 IP의 유저 수
            "fingerprint_collision" to 0,               // 핑거프린트 충돌 수
            "scroll_events" to if (isHeadless) 0 else 5,                  // 스크롤 이벤트 수
            "page_dwell_time" to if (isHeadless) 0.5 else 30.0,           // 페이지 체류 시간(초)
            "time_before_event" to 60.0                 // 이벤트 시작 전 접속 시간(초)
        )
    }
}
