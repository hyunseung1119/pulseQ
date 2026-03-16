package com.pulseq.api.controller

import com.pulseq.api.dto.*
import com.pulseq.core.service.EventService
import com.pulseq.core.service.QueueService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/queues")
class QueueController(
    private val queueService: QueueService,
    private val eventService: EventService
) {
    @PostMapping("/{eventId}/enter")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun enter(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID,
        @Valid @RequestBody request: QueueEnterRequest
    ): ApiResponse<QueueEnterResponse> {
        val tid = UUID.fromString(tenantId)
        val event = eventService.findById(eventId, tid)

        val entry = queueService.enter(
            eventId = eventId,
            tenantId = tid,
            userId = request.userId,
            ipAddress = request.metadata?.ip,
            userAgent = request.metadata?.userAgent,
            fingerprint = request.metadata?.fingerprint
        )

        return ApiResponse.ok(QueueEnterResponse.from(entry, event.rateLimit))
    }

    @GetMapping("/{eventId}/position/{queueTicket}")
    suspend fun getPosition(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID,
        @PathVariable queueTicket: String
    ): ApiResponse<QueuePositionResponse> {
        val position = queueService.getPosition(eventId, UUID.fromString(tenantId), queueTicket)
        return ApiResponse.ok(QueuePositionResponse.from(position))
    }

    @GetMapping("/{eventId}/status")
    suspend fun getQueueStatus(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID
    ): ApiResponse<QueueStatusResponse> {
        val stats = queueService.getQueueStatus(eventId, UUID.fromString(tenantId))
        return ApiResponse.ok(QueueStatusResponse.from(stats))
    }

    @PostMapping("/{eventId}/verify")
    suspend fun verifyToken(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID,
        @Valid @RequestBody request: VerifyTokenRequest
    ): ApiResponse<VerifyTokenResponse> {
        val token = queueService.verifyEntryToken(eventId, UUID.fromString(tenantId), request.entryToken)
        return ApiResponse.ok(VerifyTokenResponse.from(token))
    }

    @DeleteMapping("/{eventId}/leave/{queueTicket}")
    suspend fun leave(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID,
        @PathVariable queueTicket: String
    ): ApiResponse<QueueLeaveResponse> {
        val entry = queueService.leave(eventId, UUID.fromString(tenantId), queueTicket)
        return ApiResponse.ok(
            QueueLeaveResponse(
                queueTicket = entry.queueTicket,
                status = entry.status,
                leftAt = entry.completedAt!!
            )
        )
    }

    /**
     * 대기열 처리 트리거 (스케줄러 대신 수동 호출 가능).
     * 프로덕션에서는 스케줄러가 자동 호출.
     */
    @PostMapping("/{eventId}/process")
    suspend fun processQueue(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID
    ): ApiResponse<Map<String, Any>> {
        // 테넌트 소유 확인
        eventService.findById(eventId, UUID.fromString(tenantId))
        val tokens = queueService.processQueue(eventId)
        return ApiResponse.ok(
            mapOf(
                "processed" to tokens.size,
                "tokens" to tokens.map {
                    mapOf("userId" to it.userId, "expiresAt" to it.expiresAt.toString())
                }
            )
        )
    }
}
