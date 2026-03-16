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

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(PulseQException::class)
    fun handlePulseQException(
        ex: PulseQException,
        exchange: ServerWebExchange
    ): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail(
            type = "https://pulseq.io/errors/${ex.errorType}",
            title = ex.errorType.replace("-", " ").replaceFirstChar { it.uppercase() },
            status = ex.status,
            detail = ex.message,
            instance = exchange.request.path.value()
        )
        return ResponseEntity.status(ex.status).body(problem)
    }

    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidation(
        ex: WebExchangeBindException,
        exchange: ServerWebExchange
    ): ResponseEntity<ProblemDetail> {
        val errors = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "Invalid") }
        val problem = ProblemDetail(
            type = "https://pulseq.io/errors/validation-failed",
            title = "Validation Failed",
            status = 400,
            detail = "Request validation failed",
            instance = exchange.request.path.value(),
            extensions = mapOf("errors" to errors)
        )
        return ResponseEntity.badRequest().body(problem)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(
        ex: Exception,
        exchange: ServerWebExchange
    ): ResponseEntity<ProblemDetail> {
        log.error("Unhandled exception at {}: {}", exchange.request.path.value(), ex.message, ex)
        val problem = ProblemDetail(
            type = "https://pulseq.io/errors/internal-error",
            title = "Internal Server Error",
            status = 500,
            detail = "An unexpected error occurred",
            instance = exchange.request.path.value()
        )
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem)
    }
}
