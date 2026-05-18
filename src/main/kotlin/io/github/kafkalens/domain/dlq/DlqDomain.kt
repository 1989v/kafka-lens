package io.github.kafkalens.domain.dlq

import io.github.kafkalens.domain.message.MessageRecord
import java.time.Instant

/**
 * The mapping from a "live" topic to its DLQ topic.
 *
 * Source = AUTO when derived from a cluster's `dlqNamingPatterns`; MANUAL when an
 * operator added or overrode it via the UI. Manual entries win over auto-detected ones.
 */
data class TopicDlqMapping(
    val clusterId: String,
    val originTopic: String,
    val dlqTopic: String,
    val source: Source,
    val detectedAt: Instant,
    val confidence: Confidence = Confidence.HIGH,
) {
    enum class Source { AUTO, MANUAL }
    enum class Confidence { HIGH, MEDIUM, LOW }
}

/**
 * A message sitting in a DLQ topic, with the origin metadata reconstructed from
 * Spring Kafka / kafka-clients dead-letter headers when present.
 */
data class DlqMessage(
    val record: MessageRecord,
    val originTopic: String?,
    val originPartition: Int?,
    val originOffset: Long?,
    val failureReason: String?,
    val exceptionClass: String?,
    val stacktrace: String?,
    val retryCount: Int?,
    val lastAttemptAt: Instant?,
    val correlationId: String?,
) {
    /** True if we have enough origin info to reprocess back to the source topic. */
    val isReprocessable: Boolean get() = !originTopic.isNullOrBlank()
}

data class ReprocessJob(
    val id: String,
    val clusterId: String,
    val dlqTopic: String,
    val originTopic: String,
    val mode: Mode,
    val status: Status,
    val requestedBy: String,
    val createdAt: Instant,
    val completedAt: Instant?,
    val totalRequested: Int,
    val succeeded: Int,
    val failed: Int,
    val notes: String?,
) {
    enum class Mode { SINGLE, GROUP, ALL }
    enum class Status { PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED }
}
