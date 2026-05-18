package io.github.kafkalens.infrastructure.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kafkalens.domain.message.MessageRecord
import io.github.kafkalens.domain.ports.MessageScannerPort
import io.github.kafkalens.domain.ports.ProgressSink
import io.github.kafkalens.domain.search.SearchProgress
import io.github.kafkalens.domain.search.SearchQuery
import io.github.kafkalens.domain.search.SearchResult
import io.github.kafkalens.domain.search.SearchResult.LimitKind
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndTimestamp
import org.apache.kafka.common.TopicPartition
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val log = KotlinLogging.logger {}

@Component
class MessageScannerAdapter(
    private val factory: KafkaClientFactory,
    private val objectMapper: ObjectMapper,
) : MessageScannerPort {

    override fun scan(
        jobId: String,
        query: SearchQuery,
        progress: ProgressSink,
        cancelled: () -> Boolean,
    ): SearchResult {
        val started = System.nanoTime()
        val deadlineNanos = started + Duration.ofSeconds(query.timeoutSeconds).toNanos()
        val limitsHit = mutableSetOf<LimitKind>()
        val matched = ArrayList<MessageRecord>(query.maxResults.coerceAtMost(1000))
        var scanned = 0L
        var completed = false
        var cancelledByUser = false

        val groupId = "kafka-lens-search-${jobId.take(8)}-${UUID.randomUUID().toString().take(6)}"
        factory.newConsumer(query.clusterId, groupId).use { consumer ->
            val admin = factory.admin(query.clusterId)

            val assignments = resolveAssignments(query, consumer, admin)
            if (assignments.isEmpty()) {
                return SearchResult(jobId, emptyList(), 0, elapsedMs(started), completed = true, cancelled = false)
            }
            consumer.assign(assignments.map { it.tp })

            // Seek by time when from is set; otherwise seek to beginning or fromOffset.
            seekStartingPositions(consumer, admin, assignments, query)

            val endOffsets = consumer.endOffsets(assignments.map { it.tp }, Duration.ofSeconds(10))
            var lastProgressEmit = System.nanoTime()

            outer@ while (true) {
                if (cancelled()) { cancelledByUser = true; break }
                if (System.nanoTime() >= deadlineNanos) { limitsHit += LimitKind.TIMEOUT; break }
                if (matched.size >= query.maxResults) { limitsHit += LimitKind.MAX_RESULTS; break }
                if (scanned >= query.maxScanMessages) { limitsHit += LimitKind.MAX_SCAN_MESSAGES; break }
                if (allCaughtUp(consumer, assignments, endOffsets, query)) { completed = true; break }

                val records = consumer.poll(Duration.ofMillis(500))
                if (records.isEmpty) continue

                for (record in records) {
                    scanned++
                    val ts = Instant.ofEpochMilli(record.timestamp())
                    if (query.to != null && ts.isAfter(query.to)) continue
                    if (query.toOffset != null && record.offset() > query.toOffset) continue
                    val msg = toMessage(record)
                    if (matches(msg, query)) {
                        matched.add(msg)
                        if (matched.size >= query.maxResults) { limitsHit += LimitKind.MAX_RESULTS; break@outer }
                    }
                    if (scanned >= query.maxScanMessages) { limitsHit += LimitKind.MAX_SCAN_MESSAGES; break@outer }
                }

                val now = System.nanoTime()
                if (now - lastProgressEmit > 200_000_000) {
                    lastProgressEmit = now
                    progress.emit(
                        SearchProgress(
                            jobId = jobId,
                            scanned = scanned,
                            matched = matched.size,
                            currentTopic = records.first().topic(),
                            currentPartition = records.first().partition(),
                            elapsedMs = elapsedMs(started),
                        ),
                    )
                }
            }
        }

        return SearchResult(
            jobId = jobId,
            matched = matched,
            scannedCount = scanned,
            durationMs = elapsedMs(started),
            completed = completed && !cancelledByUser && limitsHit.isEmpty(),
            cancelled = cancelledByUser,
            limitsHit = limitsHit.toList(),
        )
    }

    private fun resolveAssignments(
        query: SearchQuery,
        consumer: org.apache.kafka.clients.consumer.KafkaConsumer<ByteArray, ByteArray>,
        admin: org.apache.kafka.clients.admin.AdminClient,
    ): List<Assignment> {
        val out = ArrayList<Assignment>()
        for (topic in query.topics) {
            val parts = consumer.partitionsFor(topic, Duration.ofSeconds(10))?.map { it.partition() } ?: emptyList()
            val selected = query.partitions?.let { wanted -> parts.filter { it in wanted } } ?: parts
            for (p in selected) {
                out += Assignment(TopicPartition(topic, p))
            }
        }
        return out
    }

    private fun seekStartingPositions(
        consumer: org.apache.kafka.clients.consumer.KafkaConsumer<ByteArray, ByteArray>,
        admin: org.apache.kafka.clients.admin.AdminClient,
        assignments: List<Assignment>,
        query: SearchQuery,
    ) {
        val tps = assignments.map { it.tp }
        if (query.from != null) {
            val req = tps.associateWith { OffsetSpec.forTimestamp(query.from.toEpochMilli()) }
            val seekTargets: Map<TopicPartition, OffsetAndTimestamp> = runCatching {
                admin.listOffsets(req).all().get().mapValues { (_, info) ->
                    OffsetAndTimestamp(info.offset().coerceAtLeast(0), info.timestamp())
                }
            }.getOrDefault(emptyMap())
            tps.forEach { tp ->
                val target = seekTargets[tp]?.offset() ?: 0L
                consumer.seek(tp, target.coerceAtLeast(0L))
            }
        } else if (query.fromOffset != null) {
            tps.forEach { consumer.seek(it, query.fromOffset) }
        } else {
            consumer.seekToBeginning(tps)
        }
    }

    private fun allCaughtUp(
        consumer: org.apache.kafka.clients.consumer.KafkaConsumer<ByteArray, ByteArray>,
        assignments: List<Assignment>,
        endOffsets: Map<TopicPartition, Long>,
        query: SearchQuery,
    ): Boolean {
        for (a in assignments) {
            val end = endOffsets[a.tp] ?: continue
            val pos = runCatching { consumer.position(a.tp, Duration.ofSeconds(2)) }.getOrElse { return false }
            val ceiling = query.toOffset?.let { minOf(end, it + 1) } ?: end
            if (pos < ceiling) return false
        }
        return true
    }

    private fun toMessage(record: ConsumerRecord<ByteArray, ByteArray>): MessageRecord {
        val key = record.key()?.toString(StandardCharsets.UTF_8)
        val value = record.value()?.toString(StandardCharsets.UTF_8)
        val headers = record.headers().associate { h ->
            h.key() to (h.value()?.toString(StandardCharsets.UTF_8) ?: "")
        }
        return MessageRecord(
            topic = record.topic(),
            partition = record.partition(),
            offset = record.offset(),
            timestamp = Instant.ofEpochMilli(record.timestamp()),
            key = key,
            value = value,
            headers = headers,
        )
    }

    private fun matches(msg: MessageRecord, query: SearchQuery): Boolean {
        query.keyContains?.let { needle ->
            val k = msg.key ?: return false
            if (!k.contains(needle, ignoreCase = false)) return false
        }
        query.valueContains?.let { needle ->
            val v = msg.value ?: return false
            if (!v.contains(needle, ignoreCase = false)) return false
        }
        for ((hk, hv) in query.headerEquals) {
            if (msg.headers[hk] != hv) return false
        }
        if (query.jsonFieldEquals.isNotEmpty() || query.jsonFieldContains.isNotEmpty()) {
            val node = msg.value?.let { runCatching { objectMapper.readTree(it) }.getOrNull() } ?: return false
            for ((path, expected) in query.jsonFieldEquals) {
                if (readPath(node, path)?.asText() != expected) return false
            }
            for ((path, needle) in query.jsonFieldContains) {
                val text = readPath(node, path)?.asText() ?: return false
                if (!text.contains(needle, ignoreCase = false)) return false
            }
        }
        return true
    }

    private fun readPath(root: JsonNode, dotted: String): JsonNode? {
        if (dotted.isBlank()) return null
        var cur: JsonNode? = root
        for (segment in dotted.split('.')) {
            cur = cur?.get(segment) ?: return null
        }
        return cur
    }

    private fun elapsedMs(started: Long) = (System.nanoTime() - started) / 1_000_000

    private data class Assignment(val tp: TopicPartition)
}
