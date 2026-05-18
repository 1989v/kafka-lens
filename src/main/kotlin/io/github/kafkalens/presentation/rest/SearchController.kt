package io.github.kafkalens.presentation.rest

import io.github.kafkalens.application.search.SearchJobRegistry
import io.github.kafkalens.application.search.SearchMessagesUseCase
import io.github.kafkalens.domain.ports.MessageScannerPort
import io.github.kafkalens.domain.ports.ProgressSink
import io.github.kafkalens.domain.search.SearchQuery
import io.github.kafkalens.domain.search.SearchResult
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.concurrent.CustomizableThreadFactory
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Instant
import java.util.concurrent.Executors

@RestController
@RequestMapping("/api/clusters/{clusterId}")
class SearchController(
    private val search: SearchMessagesUseCase,
    private val scanner: MessageScannerPort,
    private val registry: SearchJobRegistry,
) {
    private val executor = Executors.newCachedThreadPool(CustomizableThreadFactory("kafka-lens-search-"))

    @PostMapping("/search")
    fun search(@PathVariable clusterId: String, @RequestBody req: SearchRequest): SearchResult =
        search.execute(req.toDomain(clusterId))

    @PostMapping("/jobs/{jobId}/cancel")
    fun cancel(@PathVariable jobId: String): ResponseEntity<Unit> {
        val ok = registry.cancel(jobId)
        return if (ok) ResponseEntity.accepted().build() else ResponseEntity.notFound().build()
    }

    @PostMapping("/search/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(@PathVariable clusterId: String, @RequestBody req: SearchRequest): SseEmitter {
        val emitter = SseEmitter(req.timeoutSeconds * 1000 + 30_000)
        executor.submit {
            val query = req.toDomain(clusterId)
            val handle = registry.open(query)
            try {
                emitter.send(SseEmitter.event().name("job").data(JobStarted(handle.id, Instant.now())))
                handle.subscribe { progress ->
                    runCatching { emitter.send(SseEmitter.event().name("progress").data(progress)) }
                }
                val result = scanner.scan(
                    jobId = handle.id,
                    query = query,
                    progress = ProgressSink { handle.pushProgress(it) },
                    cancelled = { handle.isCancelled() },
                )
                emitter.send(SseEmitter.event().name("result").data(result))
                emitter.complete()
            } catch (ex: Exception) {
                runCatching {
                    emitter.send(SseEmitter.event().name("error").data(mapOf("message" to (ex.message ?: "error"))))
                }
                emitter.completeWithError(ex)
            } finally {
                handle.unsubscribe()
                registry.close(handle.id)
            }
        }
        return emitter
    }

    data class JobStarted(val jobId: String, val startedAt: Instant)

    data class SearchRequest(
        val topics: List<String>,
        val partitions: List<Int>? = null,
        val from: Instant? = null,
        val to: Instant? = null,
        val fromOffset: Long? = null,
        val toOffset: Long? = null,
        val keyContains: String? = null,
        val valueContains: String? = null,
        val headerEquals: Map<String, String> = emptyMap(),
        val jsonFieldEquals: Map<String, String> = emptyMap(),
        val jsonFieldContains: Map<String, String> = emptyMap(),
        val maxResults: Int = 100,
        val maxScanMessages: Long = 100_000,
        val timeoutSeconds: Long = 120,
        val contextWindow: Int = 0,
    ) {
        fun toDomain(clusterId: String) = SearchQuery(
            clusterId = clusterId,
            topics = topics,
            partitions = partitions,
            from = from,
            to = to,
            fromOffset = fromOffset,
            toOffset = toOffset,
            keyContains = keyContains,
            valueContains = valueContains,
            headerEquals = headerEquals,
            jsonFieldEquals = jsonFieldEquals,
            jsonFieldContains = jsonFieldContains,
            maxResults = maxResults,
            maxScanMessages = maxScanMessages,
            timeoutSeconds = timeoutSeconds,
            contextWindow = contextWindow,
        )
    }
}
