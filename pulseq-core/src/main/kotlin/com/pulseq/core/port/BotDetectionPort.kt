package com.pulseq.core.port

import java.util.UUID

data class BotScoreResult(
    val userId: String,
    val botScore: Double,
    val isBot: Boolean,
    val topReasons: List<BotReason> = emptyList()
)

data class BotReason(
    val feature: String,
    val value: Double,
    val impact: Double
)

interface BotDetectionPort {
    suspend fun score(eventId: UUID, userId: String, features: Map<String, Any?>): BotScoreResult
    suspend fun isAvailable(): Boolean
}
