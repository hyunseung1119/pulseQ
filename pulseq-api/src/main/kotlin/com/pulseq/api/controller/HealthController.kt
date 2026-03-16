package com.pulseq.api.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class HealthController {

    @GetMapping("/health")
    suspend fun health(): Map<String, Any> {
        return mapOf(
            "status" to "UP",
            "version" to "0.1.0",
            "components" to mapOf(
                "postgres" to "UP",
                "redis" to "PENDING"
            )
        )
    }
}
