package io.github.kafkalens.presentation

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity

data class ApiError(val code: String, val message: String, val details: Map<String, Any?> = emptyMap()) {
    fun toResponse(status: HttpStatus): ResponseEntity<ApiError> = ResponseEntity.status(status).body(this)
}
