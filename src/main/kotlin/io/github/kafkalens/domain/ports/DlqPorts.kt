package io.github.kafkalens.domain.ports

import io.github.kafkalens.domain.dlq.DlqMessage
import io.github.kafkalens.domain.dlq.ReprocessJob
import io.github.kafkalens.domain.dlq.TopicDlqMapping
import io.github.kafkalens.domain.message.MessageRecord
import java.time.Duration

interface DlqMappingPort {
    fun list(clusterId: String): List<TopicDlqMapping>
    fun getByDlq(clusterId: String, dlqTopic: String): TopicDlqMapping?
    fun getByOrigin(clusterId: String, originTopic: String): List<TopicDlqMapping>
    fun upsertManual(mapping: TopicDlqMapping)
    fun deleteManual(clusterId: String, originTopic: String, dlqTopic: String)
    /** Pure helpers for the auto-detection step; persisted as snapshots not records. */
    fun rememberAutoDetected(mappings: List<TopicDlqMapping>)
}

interface DlqReaderPort {
    fun readDlqPage(
        clusterId: String,
        dlqTopic: String,
        partition: Int?,
        fromOffset: Long?,
        limit: Int,
    ): List<DlqMessage>

    fun readByFingerprint(clusterId: String, dlqTopic: String, partition: Int, offset: Long): DlqMessage?
}

interface ReprocessHistoryPort {
    fun save(job: ReprocessJob)
    fun list(clusterId: String, limit: Int): List<ReprocessJob>
    fun get(id: String): ReprocessJob?
}

interface DuplicateDetectionPort {
    /** Returns true if the same DLQ message was reprocessed inside the window. */
    fun wasRecentlyReprocessed(fingerprint: String): Boolean
    fun markReprocessed(fingerprint: String, ttl: Duration)
}

/**
 * Re-exposing MessageRecord here so DLQ-specific use cases don't have to import
 * across packages just for the fingerprint helper. Stays in the ports module on
 * purpose — it's part of the contract DLQ adapters need to satisfy.
 */
typealias DlqRecordRef = MessageRecord
