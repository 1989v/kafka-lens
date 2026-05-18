package io.github.kafkalens.presentation.rest

import io.github.kafkalens.application.publish.PublishMessageUseCase
import io.github.kafkalens.domain.ports.PublishResult
import io.github.kafkalens.presentation.ActorResolver
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/clusters/{clusterId}")
class PublishController(
    private val publish: PublishMessageUseCase,
    private val actorResolver: ActorResolver,
) {

    @PostMapping("/publish")
    fun publish(
        @PathVariable clusterId: String,
        @RequestBody req: Request,
        request: HttpServletRequest,
    ): PublishResult {
        return publish.execute(
            clusterId = clusterId,
            topic = req.topic,
            key = req.key,
            value = req.value,
            headers = req.headers,
            actor = actorResolver.resolve(request),
        )
    }

    data class Request(
        val topic: String,
        val key: String?,
        val value: String,
        val headers: Map<String, String> = emptyMap(),
    )
}
