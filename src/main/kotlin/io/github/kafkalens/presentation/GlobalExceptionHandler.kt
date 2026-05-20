package io.github.kafkalens.presentation

import io.github.kafkalens.domain.ports.ConnectNotConfigured
import io.github.kafkalens.infrastructure.kafka.DirectDlqPublishForbidden
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val log = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ApiError> =
        ApiError("INVALID_ARGUMENT", ex.message ?: "Invalid argument").toResponse(HttpStatus.BAD_REQUEST)

    @ExceptionHandler(IllegalStateException::class)
    fun handleConflict(ex: IllegalStateException): ResponseEntity<ApiError> =
        ApiError("CONFLICT", ex.message ?: "Conflict").toResponse(HttpStatus.CONFLICT)

    @ExceptionHandler(DirectDlqPublishForbidden::class)
    fun handleDlqGuard(ex: DirectDlqPublishForbidden): ResponseEntity<ApiError> =
        ApiError("DLQ_DIRECT_PUBLISH_FORBIDDEN", ex.message ?: "Forbidden", mapOf("topic" to ex.topic))
            .toResponse(HttpStatus.FORBIDDEN)

    @ExceptionHandler(ConnectNotConfigured::class)
    fun handleConnectNotConfigured(ex: ConnectNotConfigured): ResponseEntity<ApiError> =
        ApiError("CONNECT_NOT_CONFIGURED", ex.message ?: "Kafka Connect endpoint not configured")
            .toResponse(HttpStatus.PRECONDITION_FAILED)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val detail = ex.bindingResult.fieldErrors.associate { it.field to (it.defaultMessage ?: "invalid") }
        return ApiError("VALIDATION_FAILED", "Request validation failed", detail).toResponse(HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(Exception::class)
    fun handleAny(ex: Exception): ResponseEntity<ApiError> {
        log.error(ex) { "Unhandled exception" }
        return ApiError("INTERNAL_ERROR", ex.message ?: "Unexpected error").toResponse(HttpStatus.INTERNAL_SERVER_ERROR)
    }
}
