package io.github.kafkalens.application.stats

import java.time.Instant

/**
 * A single point-in-time view of a topic's traffic state.
 *
 * - [endOffset] is the sum across all partitions, so the difference between two
 *   samples gives total messages produced in that window.
 * - [lagByGroup] maps consumer group id → total lag across the topic's
 *   partitions at the sample's instant.
 */
data class TopicSample(
    val timestamp: Instant,
    val endOffset: Long,
    val lagByGroup: Map<String, Long>,
)
