package com.pulseq.api.dto

import java.time.Instant

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val timestamp: Instant = Instant.now()
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(success = true, data = data)
        fun error(message: String): ApiResponse<Nothing> = ApiResponse(success = false, data = null)
    }
}

data class ProblemDetail(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String? = null,
    val extensions: Map<String, Any>? = null
)
