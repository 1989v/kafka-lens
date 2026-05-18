package io.github.kafkalens.application.dlq

import io.github.kafkalens.domain.dlq.DlqMessage
import io.github.kafkalens.domain.dlq.ReprocessJob
import io.github.kafkalens.domain.ports.DlqMappingPort
import io.github.kafkalens.domain.ports.DlqReaderPort
import io.github.kafkalens.domain.ports.DuplicateDetectionPort
import io.github.kafkalens.domain.ports.MessagePublisherPort
import io.github.kafkalens.domain.ports.ReprocessHistoryPort
import io.github.kafkalens.infrastructure.config.KafkaLensProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val log = KotlinLogging.logger {}

@Service
class ReprocessDlqUseCase(
    private val mappings: DlqMappingPort,
    private val reader: DlqReaderPort,
    private val publisher: MessagePublisherPort,
    private val history: ReprocessHistoryPort,
    private val dedupe: DuplicateDetectionPort,
    private val props: KafkaLensProperties,
) {
    /**
     * Reprocess one or more DLQ messages back to their origin topic. Guards:
     *  - All messages must come from the same DLQ topic and have a resolvable origin.
     *  - The DLQ→origin mapping (auto or manual) must exist.
     *  - Each message fingerprint can only be reprocessed once per dedupe window.
     *  - Single batch must stay under `dlq.reprocess.maxBatchSize`.
     *
     * The actual publish goes through [MessagePublisherPort], which itself refuses
     * direct DLQ targets — so the only way a message can land in a DLQ via Kafka
     * Lens is by *being* one that we then reprocess back out.
     */
    fun execute(
        clusterId: String,
        dlqTopic: String,
        targets: List<Target>,
        actor: String,
        mode: ReprocessJob.Mode,
        notes: String? = null,
    ): ReprocessJob {
        require(targets.isNotEmpty()) { "no DLQ messages selected for reprocess" }
        val cap = props.dlq.reprocess.maxBatchSize
        require(targets.size <= cap) { "batch size ${targets.size} exceeds cap $cap" }

        val mapping = mappings.getByDlq(clusterId, dlqTopic)
            ?: throw IllegalStateException("No origin-topic mapping registered for DLQ '$dlqTopic'. Set up the mapping first.")
        val originTopic = mapping.originTopic

        val jobId = UUID.randomUUID().toString()
        val createdAt = Instant.now()
        val dedupeTtl = parseDuration(props.dlq.reprocess.duplicateDetectionWindow)

        var succeeded = 0
        var failed = 0
        val problems = mutableListOf<String>()

        for (target in targets) {
            val dlqMsg = reader.readByFingerprint(clusterId, dlqTopic, target.partition, target.offset)
            if (dlqMsg == null) {
                failed++; problems += "missing ${target.partition}:${target.offset}"; continue
            }
            if (dlqMsg.originTopic != null && dlqMsg.originTopic != originTopic) {
                failed++; problems += "${target.partition}:${target.offset} origin=${dlqMsg.originTopic}, expected=$originTopic"; continue
            }
            if (dedupe.wasRecentlyReprocessed(dlqMsg.record.fingerprint)) {
                failed++; problems += "${target.partition}:${target.offset} duplicate"; continue
            }
            val ok = republish(clusterId, originTopic, dlqMsg)
            if (ok) {
                succeeded++
                dedupe.markReprocessed(dlqMsg.record.fingerprint, dedupeTtl)
            } else {
                failed++
                problems += "${target.partition}:${target.offset} publish-failed"
            }
        }

        val job = ReprocessJob(
            id = jobId,
            clusterId = clusterId,
            dlqTopic = dlqTopic,
            originTopic = originTopic,
            mode = mode,
            status = if (failed == 0) ReprocessJob.Status.COMPLETED
                else if (succeeded == 0) ReprocessJob.Status.FAILED
                else ReprocessJob.Status.COMPLETED,
            requestedBy = actor,
            createdAt = createdAt,
            completedAt = Instant.now(),
            totalRequested = targets.size,
            succeeded = succeeded,
            failed = failed,
            notes = notes ?: problems.take(20).joinToString(" | ").ifBlank { null },
        )
        history.save(job)
        return job
    }

    private fun republish(clusterId: String, originTopic: String, msg: io.github.kafkalens.domain.dlq.DlqMessage): Boolean =
        try {
            publisher.publish(
                clusterId = clusterId,
                topic = originTopic,
                key = msg.record.key,
                value = msg.record.value.orEmpty(),
                headers = msg.record.headers + ("x-reprocessed-from" to msg.record.fingerprint),
            )
            true
        } catch (ex: Exception) {
            log.warn(ex) { "Failed to republish ${msg.record.fingerprint} to $originTopic" }
            false
        }

    private fun parseDuration(raw: String): Duration {
        val trimmed = raw.trim().lowercase()
        return when {
            trimmed.endsWith("h") -> Duration.ofHours(trimmed.dropLast(1).toLong())
            trimmed.endsWith("m") -> Duration.ofMinutes(trimmed.dropLast(1).toLong())
            trimmed.endsWith("s") -> Duration.ofSeconds(trimmed.dropLast(1).toLong())
            trimmed.endsWith("d") -> Duration.ofDays(trimmed.dropLast(1).toLong())
            else -> Duration.parse(raw)
        }
    }

    data class Target(val partition: Int, val offset: Long)
}
