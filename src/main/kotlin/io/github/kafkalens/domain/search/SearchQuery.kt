package io.github.kafkalens.domain.search

import io.github.kafkalens.domain.message.MessageRecord
import java.time.Instant

/**
 * On-demand consume + filter. We do NOT pre-index in ES; Kafka is the source of truth.
 *
 * Filters compose with AND semantics — every set predicate must match.
 * JSON predicates are evaluated server-side on the message value when possible,
 * skipped silently when the value isn't valid JSON.
 */
data class SearchQuery(
    val clusterId: String,
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
    init {
        require(topics.isNotEmpty()) { "at least one topic must be specified" }
        require(maxResults in 1..10_000) { "maxResults must be in [1, 10000]" }
        require(maxScanMessages in 1..10_000_000) { "maxScanMessages must be in [1, 10_000_000]" }
        require(timeoutSeconds in 1..3600) { "timeoutSeconds must be in [1, 3600]" }
        require(contextWindow in 0..50) { "contextWindow must be in [0, 50]" }
        if (from != null && to != null) require(!from.isAfter(to)) { "from must not be after to" }
        if (fromOffset != null && toOffset != null) require(fromOffset <= toOffset) { "fromOffset must be <= toOffset" }
    }

    fun hasAnyFilter(): Boolean =
        keyContains != null ||
            valueContains != null ||
            headerEquals.isNotEmpty() ||
            jsonFieldEquals.isNotEmpty() ||
            jsonFieldContains.isNotEmpty()
}

data class SearchResult(
    val jobId: String,
    val matched: List<MessageRecord>,
    val scannedCount: Long,
    val durationMs: Long,
    val completed: Boolean,
    val cancelled: Boolean,
    val limitsHit: List<LimitKind> = emptyList(),
) {
    enum class LimitKind { MAX_RESULTS, MAX_SCAN_MESSAGES, TIMEOUT }
}

data class SearchProgress(
    val jobId: String,
    val scanned: Long,
    val matched: Int,
    val currentTopic: String?,
    val currentPartition: Int?,
    val elapsedMs: Long,
)

data class ScanLimits(
    val maxMessagesPerJob: Long,
    val maxScanDurationSeconds: Long,
    val defaultPageSize: Int,
)
