package io.github.kafkalens.application.search

import io.github.kafkalens.domain.search.SearchProgress
import io.github.kafkalens.domain.search.SearchQuery
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks in-flight search jobs so the SSE endpoint can subscribe to progress and
 * the REST cancel endpoint can flip the cooperative-cancel flag.
 *
 * Intentionally in-memory only — Kafka Lens runs as a single self-hosted instance,
 * so jobs don't survive restart and don't need a shared store.
 */
@Component
class SearchJobRegistry {
    private val jobs = ConcurrentHashMap<String, JobHandle>()

    fun open(query: SearchQuery): JobHandle {
        val id = UUID.randomUUID().toString()
        val handle = JobHandle(id, query, Instant.now())
        jobs[id] = handle
        return handle
    }

    fun get(id: String): JobHandle? = jobs[id]

    fun close(id: String) {
        jobs.remove(id)
    }

    fun cancel(id: String): Boolean {
        val handle = jobs[id] ?: return false
        handle.cancel()
        return true
    }
}

class JobHandle(
    val id: String,
    val query: SearchQuery,
    val startedAt: Instant,
) {
    private val cancelled = AtomicBoolean(false)
    private val latest = AtomicReference<SearchProgress?>(null)
    @Volatile private var listener: ((SearchProgress) -> Unit)? = null

    fun cancel() {
        cancelled.set(true)
    }

    fun isCancelled(): Boolean = cancelled.get()

    fun pushProgress(progress: SearchProgress) {
        latest.set(progress)
        listener?.invoke(progress)
    }

    fun snapshot(): SearchProgress? = latest.get()

    fun subscribe(listener: (SearchProgress) -> Unit) {
        this.listener = listener
        latest.get()?.let(listener)
    }

    fun unsubscribe() {
        this.listener = null
    }
}
