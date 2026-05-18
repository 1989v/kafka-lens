package io.github.kafkalens.presentation

import jakarta.servlet.http.HttpServletRequest
import org.springframework.stereotype.Component

/**
 * Returns the principal name for the current request. For M1 we treat every
 * request as anonymous; once auth modes (basic/oidc) land we'll plug them in here
 * without touching every controller.
 */
@Component
class ActorResolver {
    fun resolve(request: HttpServletRequest? = null): String {
        val header = request?.getHeader("X-Kafka-Lens-Actor")?.trim()
        return header?.takeIf { it.isNotEmpty() } ?: "anonymous"
    }
}
