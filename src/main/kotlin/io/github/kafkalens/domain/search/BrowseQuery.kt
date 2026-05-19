package io.github.kafkalens.domain.search

import io.github.kafkalens.domain.message.MessageRecord
import java.time.Instant

/**
 * Single-topic browse — the bread-and-butter "show me a page of this topic"
 * operation that mirrors kafka-ui's Messages tab.
 *
 * Unlike [SearchQuery] (which spans topics + filters + correlation-id traces),
 * a browse always targets exactly one topic and yields a forward-going page
 * with a cursor for prev/next pagination.
 *
 * `keyContains`/`valueContains` are best-effort post-filters applied on the
 * fetched page; they DON'T drive the seek. That keeps pagination
 * deterministic.
 */
data class BrowseQuery(
    val clusterId: String,
    val topic: String,
    val mode: BrowseMode,
    val partitions: List<Int>? = null,
    val pageSize: Int = 50,
    val fromOffset: Map<Int, Long>? = null,
    val fromTimestamp: Instant? = null,
    val toTimestamp: Instant? = null,
    val keyContains: String? = null,
    val valueContains: String? = null,
    val timeoutSeconds: Long = 30,
) {
    init {
        require(topic.isNotBlank()) { "topic must not be blank" }
        require(pageSize in 1..1000) { "pageSize must be in [1, 1000]" }
        require(timeoutSeconds in 1..600) { "timeoutSeconds must be in [1, 600]" }
        if (mode == BrowseMode.FROM_TIMESTAMP || mode == BrowseMode.RANGE) {
            require(fromTimestamp != null) { "fromTimestamp required for $mode" }
        }
        if (mode == BrowseMode.RANGE) {
            require(toTimestamp != null && !fromTimestamp!!.isAfter(toTimestamp)) {
                "RANGE requires toTimestamp >= fromTimestamp"
            }
        }
        if (mode == BrowseMode.FROM_OFFSET) {
            require(!fromOffset.isNullOrEmpty()) { "fromOffset required for FROM_OFFSET" }
        }
    }
}

enum class BrowseMode {
    /** Read the tail — last [BrowseQuery.pageSize] messages across the targeted partitions. */
    LATEST,

    /** Read from the beginning of each targeted partition. */
    EARLIEST,

    /** Start at the offsets provided in [BrowseQuery.fromOffset]. Used by next-page navigation. */
    FROM_OFFSET,

    /** Seek by timestamp; read forward to end of partition. */
    FROM_TIMESTAMP,

    /** Bounded scan from [BrowseQuery.fromTimestamp] up to [BrowseQuery.toTimestamp]. */
    RANGE,
}

data class BrowsePage(
    val messages: List<MessageRecord>,
    val nextCursor: Map<Int, Long>?,
    val hasMore: Boolean,
    val partitionsScanned: List<Int>,
    val durationMs: Long,
)
