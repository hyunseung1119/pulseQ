package com.pulseq.api.config

import com.pulseq.api.dto.ProblemDetail
import com.pulseq.core.exception.PulseQException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.slf4j.LoggerFactory
import org.springframework.web.server.ServerWebExchange

/**
 * 전역 예외 처리기 — RFC 9457 (Problem Details) 표준에 따라 에러 응답을 생성한다.
 * 모든 컨트롤러에서 발생하는 예외를 한 곳에서 처리.
 *
 * 응답 형식 예시:
 * {
 *   "type": "https://pulseq.io/errors/event-not-found",
 *   "title": "Event Not Found",
 *   "status": 404,
 *   "detail": "Event with ID abc-123 does not exist",
 *   "instance": "/api/v1/events/abc-123"
 * }
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * PulseQ 커스텀 예외 처리 — 비즈니스 로직 에러.
     * EventNotFoundException(404), BotDetectedException(403),
     * AlreadyInQueueException(409), QueueFullException(429) 등.
     */
    @ExceptionHandler(PulseQException::class)
    fun handlePulseQException(
        ex: PulseQException,
        exchange: ServerWebExchange
    ): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail(
            type = "https://pulseq.io/errors/${ex.errorType}",
            // errorType "event-not-found" → "Event not found" (사람이 읽을 수 있는 제목)
            title = ex.errorType.replace("-", " ").replaceFirstChar { it.uppercase() },
            status = ex.status,
            detail = ex.message,
            instance = exchange.request.path.value()  // 요청 경로 포함
        )
        return ResponseEntity.status(ex.status).body(problem)
    }

    /**
     * 요청 바디 유효성 검증 실패 처리 — @Valid 어노테이션 위반 시.
     * 필드별 에러 메시지를 extensions에 포함하여 반환.
     */
    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidation(
        ex: WebExchangeBindException,
        exchange: ServerWebExchange
    ): ResponseEntity<ProblemDetail> {
        // 필드별 에러 메시지 추출: { "name": "must not be blank", "maxCapacity": "must be > 0" }
        val errors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid") }
        val problem = ProblemDetail(
            type = "https://pulseq.io/errors/validation-failed",
            title = "Validation Failed",
            status = 400,
            detail = "Request validation failed",
            instance = exchange.request.path.value(),
            extensions = mapOf("errors" to errors)  // 필드별 상세 에러
        )
        return ResponseEntity.badRequest().body(problem)
    }

    /**
     * 예상치 못한 예외 처리 — 위 핸들러에 매칭되지 않는 모든 예외.
     * 내부 에러 메시지를 클라이언트에 노출하지 않고, 일반적인 500 응답 반환.
     * 실제 에러 내용은 서버 로그에만 기록 (보안).
     */
    @ExceptionHandler(Exception::class)
    fun handleGeneral(
        ex: Exception,
        exchange: ServerWebExchange
    ): ResponseEntity<ProblemDetail> {
        // 서버 로그에 스택 트레이스 포함하여 기록 (디버깅용)
        log.error("Unhandled exception at {}: {}", exchange.request.path.value(), ex.message, ex)
        val problem = ProblemDetail(
            type = "https://pulseq.io/errors/internal-error",
            title = "Internal Server Error",
            status = 500,
            detail = "An unexpected error occurred",  // 내부 정보 미노출
            instance = exchange.request.path.value()
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem)
    }
}
