package com.pulseq.api.controller

import com.pulseq.api.dto.*
import com.pulseq.core.service.EventService
import com.pulseq.core.service.QueueService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * 대기열 컨트롤러 — 입장, 위치 조회, 상태 조회, 토큰 검증, 퇴장, 수동 처리 엔드포인트.
 * JWT 인증된 테넌트만 접근 가능 (@AuthenticationPrincipal로 tenantId 추출).
 */
@RestController
@RequestMapping("/api/v1/queues")
class QueueController(
    private val queueService: QueueService,  // 대기열 핵심 비즈니스 로직
    private val eventService: EventService   // 이벤트 조회 (테넌트 소유 확인)
) {
    /**
     * POST /queues/{eventId}/enter — 대기열 입장
     * 201 Created 반환. 봇 탐지, 중복 체크, 정원 체크 후 대기열에 추가.
     */
    @PostMapping("/{eventId}/enter")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun enter(
        @AuthenticationPrincipal tenantId: String,    // JWT에서 추출한 테넌트 ID
        @PathVariable eventId: UUID,                   // 경로 파라미터: 이벤트 ID
        @Valid @RequestBody request: QueueEnterRequest  // 요청 바디: userId, metadata
    ): ApiResponse<QueueEnterResponse> {
        val tid = UUID.fromString(tenantId)
        // 이벤트 존재 + 테넌트 소유 확인 (BOLA 방지)
        val event = eventService.findById(eventId, tid)

        // 대기열 입장 처리 (Redis + DB + Kafka)
        val entry = queueService.enter(
            eventId = eventId,
            tenantId = tid,
            userId = request.userId,
            ipAddress = request.metadata?.ip,           // 봇 탐지용 IP
            userAgent = request.metadata?.userAgent,     // 봇 탐지용 User-Agent
            fingerprint = request.metadata?.fingerprint  // 봇 탐지용 브라우저 핑거프린트
        )

        return ApiResponse.ok(QueueEnterResponse.from(entry, event.rateLimit))
    }

    /**
     * GET /queues/{eventId}/position/{queueTicket} — 대기 위치 조회
     * 티켓 번호로 현재 몇 번째인지, 예상 대기 시간 등을 반환.
     */
    @GetMapping("/{eventId}/position/{queueTicket}")
    suspend fun getPosition(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID,
        @PathVariable queueTicket: String  // 입장 시 발급된 대기열 티켓
    ): ApiResponse<QueuePositionResponse> {
        val position = queueService.getPosition(eventId, UUID.fromString(tenantId), queueTicket)
        return ApiResponse.ok(QueuePositionResponse.from(position))
    }

    /**
     * GET /queues/{eventId}/status — 큐 전체 상태 조회
     * 대기 인원, 처리 완료, 이탈, 봇 차단 수 등 대시보드용 데이터.
     */
    @GetMapping("/{eventId}/status")
    suspend fun getQueueStatus(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID
    ): ApiResponse<QueueStatusResponse> {
        val stats = queueService.getQueueStatus(eventId, UUID.fromString(tenantId))
        return ApiResponse.ok(QueueStatusResponse.from(stats))
    }

    /**
     * POST /queues/{eventId}/verify — 입장 토큰 검증
     * 클라이언트가 받은 입장 토큰이 유효한지 확인 (결제 페이지 접근 권한).
     */
    @PostMapping("/{eventId}/verify")
    suspend fun verifyToken(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID,
        @Valid @RequestBody request: VerifyTokenRequest
    ): ApiResponse<VerifyTokenResponse> {
        val token = queueService.verifyEntryToken(eventId, UUID.fromString(tenantId), request.entryToken)
        return ApiResponse.ok(VerifyTokenResponse.from(token))
    }

    /**
     * DELETE /queues/{eventId}/leave/{queueTicket} — 대기열 퇴장
     * 사용자가 자발적으로 대기열을 떠날 때 호출.
     */
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
                leftAt = entry.completedAt!!  // 퇴장 시 completedAt은 항상 존재
            )
        )
    }

    /**
     * POST /queues/{eventId}/process — 대기열 수동 처리 트리거
     * 스케줄러(QueueScheduler)가 자동 호출하지만, 대시보드에서 수동 실행도 가능.
     * 상위 N명(rateLimit)에게 입장 토큰 발급.
     */
    @PostMapping("/{eventId}/process")
    suspend fun processQueue(
        @AuthenticationPrincipal tenantId: String,
        @PathVariable eventId: UUID
    ): ApiResponse<Map<String, Any>> {
        // 테넌트 소유 확인 (다른 테넌트의 이벤트 처리 방지)
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
