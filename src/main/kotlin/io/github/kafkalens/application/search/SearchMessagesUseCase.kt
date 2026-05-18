package io.github.kafkalens.application.search

import io.github.kafkalens.domain.ports.MessageScannerPort
import io.github.kafkalens.domain.ports.ProgressSink
import io.github.kafkalens.domain.search.SearchQuery
import io.github.kafkalens.domain.search.SearchResult
import org.springframework.stereotype.Service

@Service
class SearchMessagesUseCase(
    private val scanner: MessageScannerPort,
    private val jobs: SearchJobRegistry,
) {
    fun execute(query: SearchQuery): SearchResult {
        val handle = jobs.open(query)
        try {
            return scanner.scan(
                jobId = handle.id,
                query = query,
                progress = ProgressSink { handle.pushProgress(it) },
                cancelled = { handle.isCancelled() },
            )
        } finally {
            jobs.close(handle.id)
        }
    }
}
