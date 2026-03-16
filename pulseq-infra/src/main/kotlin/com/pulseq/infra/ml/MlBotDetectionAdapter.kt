package com.pulseq.infra.ml

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.pulseq.core.port.BotDetectionPort
import com.pulseq.core.port.BotReason
import com.pulseq.core.port.BotScoreResult
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.util.UUID

@Component
class MlBotDetectionAdapter(
    @Value("\${pulseq.ml.base-url:http://localhost:8000}") private val mlBaseUrl: String,
    private val objectMapper: ObjectMapper
) : BotDetectionPort {

    private val log = LoggerFactory.getLogger(javaClass)
    private val webClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl(mlBaseUrl)
            .build()
    }

    override suspend fun score(eventId: UUID, userId: String, features: Map<String, Any?>): BotScoreResult {
        val request = mapOf(
            "event_id" to eventId.toString(),
            "user_id" to userId,
            "features" to features
        )

        val responseBody = webClient.post()
            .uri("/ml/bot-score")
            .bodyValue(request)
            .retrieve()
            .bodyToMono<String>()
            .awaitFirstOrNull()
            ?: throw RuntimeException("Empty response from ML service")

        val response: Map<String, Any?> = objectMapper.readValue(responseBody)

        val topReasons = (response["top_reasons"] as? List<*>)?.mapNotNull { reason ->
            val map = reason as? Map<*, *> ?: return@mapNotNull null
            BotReason(
                feature = map["feature"]?.toString() ?: "",
                value = (map["value"] as? Number)?.toDouble() ?: 0.0,
                impact = (map["impact"] as? Number)?.toDouble() ?: 0.0
            )
        } ?: emptyList()

        return BotScoreResult(
            userId = userId,
            botScore = (response["bot_score"] as Number).toDouble(),
            isBot = response["is_bot"] as Boolean,
            topReasons = topReasons
        )
    }

    override suspend fun isAvailable(): Boolean {
        return try {
            val response = webClient.get()
                .uri("/ml/health")
                .retrieve()
                .bodyToMono<String>()
                .awaitFirstOrNull()

            response?.contains("UP") == true
        } catch (e: Exception) {
            log.debug("ML service unavailable: {}", e.message)
            false
        }
    }
}
