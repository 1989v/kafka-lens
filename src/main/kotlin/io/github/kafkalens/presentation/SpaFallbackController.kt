package io.github.kafkalens.presentation

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Forwards client-side routes back to the SPA entry point so React Router can
 * resolve them on the client. Owns only paths that look like a single route
 * segment without an extension; static assets and `/api`/`/sse` are routed
 * elsewhere and never reach this controller.
 */
@Controller
class SpaFallbackController {

    @GetMapping(value = ["/", "/{path:[^.]+}", "/{path1:[^.]+}/{path2:[^.]+}"])
    fun forwardToIndex(): String = "forward:/index.html"
}
