package io.github.kafkalens.domain.ports

import io.github.kafkalens.domain.search.SearchProgress
import io.github.kafkalens.domain.search.SearchQuery
import io.github.kafkalens.domain.search.SearchResult

/**
 * Performs an on-demand scan-and-filter against Kafka topics for a single SearchQuery.
 *
 * Implementations create a transient consumer group and tear it down after the scan,
 * honor the limits set in the query (maxResults, maxScanMessages, timeoutSeconds),
 * and emit periodic progress so long-running scans can be reported through SSE.
 *
 * The cancelled supplier is polled cooperatively; implementations must check it
 * at least between partition fetches.
 */
fun interface ProgressSink {
    fun emit(progress: SearchProgress)
}

interface MessageScannerPort {
    fun scan(
        jobId: String,
        query: SearchQuery,
        progress: ProgressSink = ProgressSink { },
        cancelled: () -> Boolean = { false },
    ): SearchResult
}
