package io.github.kafkalens.infrastructure.kafka

import io.github.kafkalens.domain.message.MessageRecord
import io.github.kafkalens.domain.ports.BrowseTopicPort
import io.github.kafkalens.domain.search.BrowseMode
import io.github.kafkalens.domain.search.BrowsePage
import io.github.kafkalens.domain.search.BrowseQuery
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.UUID

private val log = KotlinLogging.logger {}

/**
 * Implements paginated browse over a single topic.
 *
 * Each call creates a transient consumer (group `kafka-lens-browse-*`) and
 * tears it down after — we explicitly DO NOT keep long-lived subscribers,
 * so two concurrent browses don't share assignments or step on each other.
 */
@Component
class BrowseTopicAdapter(private val factory: KafkaClientFactory) : BrowseTopicPort {

    override fun browse(query: BrowseQuery): BrowsePage {
        val started = System.nanoTime()
        val deadline = started + Duration.ofSeconds(query.timeoutSeconds).toNanos()
        val groupId = "kafka-lens-browse-${UUID.randomUUID().toString().take(8)}"

        factory.newConsumer(query.clusterId, groupId).use { consumer ->
            val admin = factory.admin(query.clusterId)

            val allParts = consumer.partitionsFor(query.topic, Duration.ofSeconds(10))
                ?.map { it.partition() }
                ?: return emptyPage(elapsed(started))
            val targetParts = query.partitions?.let { wanted -> allParts.filter { it in wanted } } ?: allParts
            if (targetParts.isEmpty()) return emptyPage(elapsed(started))

            val tps = targetParts.map { TopicPartition(query.topic, it) }
            consumer.assign(tps)

            val beginning = consumer.beginningOffsets(tps, Duration.ofSeconds(10))
            val end = consumer.endOffsets(tps, Duration.ofSeconds(10))

            seek(consumer, admin, query, tps, beginning, end)

            val collected = ArrayList<MessageRecord>(query.pageSize)
            val toEpochMs = query.toTimestamp?.toEpochMilli()
            val hasFilter = !query.keyContains.isNullOrEmpty() || !query.valueContains.isNullOrEmpty()

            outer@ while (collected.size < query.pageSize && System.nanoTime() < deadline) {
                val records = consumer.poll(Duration.ofMillis(500))
                if (records.isEmpty) {
                    if (allCaughtUp(consumer, tps, end)) break
                    continue
                }
                for (record in records) {
                    if (toEpochMs != null && record.timestamp() > toEpochMs) continue
                    val msg = toMessage(record)
                    if (hasFilter && !passesFilter(msg, query)) continue
                    collected.add(msg)
                    if (collected.size >= query.pageSize) break@outer
                }
            }

            val nextCursor = nextCursorFor(consumer, tps, end, query.mode)

            return BrowsePage(
                messages = if (query.mode == BrowseMode.LATEST) collected.sortedByDescending { it.timestamp } else collected.sortedBy { it.timestamp },
                nextCursor = nextCursor,
                hasMore = nextCursor != null,
                partitionsScanned = targetParts,
                durationMs = elapsed(started),
            )
        }
    }

    private fun seek(
        consumer: KafkaConsumer<ByteArray, ByteArray>,
        admin: org.apache.kafka.clients.admin.AdminClient,
        query: BrowseQuery,
        tps: List<TopicPartition>,
        beginning: Map<TopicPartition, Long>,
        end: Map<TopicPartition, Long>,
    ) {
        when (query.mode) {
            BrowseMode.EARLIEST -> tps.forEach { consumer.seek(it, beginning[it] ?: 0L) }
            BrowseMode.LATEST -> {
                // When a key/value filter is set the user is effectively asking
                // "is this in the recent traffic of the topic?" — we widen the
                // seek window dramatically so the post-filter has enough to
                // match against. Without a filter we just want the tail page.
                val hasFilter = !query.keyContains.isNullOrEmpty() || !query.valueContains.isNullOrEmpty()
                val windowMultiplier = if (hasFilter) 2000L else 1L
                val perPartition = ((query.pageSize.toLong() * windowMultiplier) / tps.size).coerceAtLeast(1L)
                tps.forEach {
                    val endOff = end[it] ?: 0L
                    val beg = beginning[it] ?: 0L
                    consumer.seek(it, (endOff - perPartition).coerceAtLeast(beg))
                }
            }
            BrowseMode.FROM_OFFSET -> {
                val map = query.fromOffset.orEmpty()
                tps.forEach { tp ->
                    val target = map[tp.partition()] ?: beginning[tp] ?: 0L
                    consumer.seek(tp, target.coerceAtLeast(beginning[tp] ?: 0L))
                }
            }
            BrowseMode.FROM_TIMESTAMP, BrowseMode.RANGE -> {
                val ts = query.fromTimestamp?.toEpochMilli() ?: error("fromTimestamp required")
                val req = tps.associateWith { OffsetSpec.forTimestamp(ts) }
                val targets = runCatching { admin.listOffsets(req).all().get() }.getOrElse {
                    log.warn(it) { "listOffsets by timestamp failed; falling back to beginning" }
                    emptyMap()
                }
                tps.forEach { tp ->
                    val target = targets[tp]?.offset()?.takeIf { it >= 0 } ?: beginning[tp] ?: 0L
                    consumer.seek(tp, target)
                }
            }
        }
    }

    private fun passesFilter(msg: MessageRecord, query: BrowseQuery): Boolean {
        val keyOk = query.keyContains?.let { needle -> msg.key?.contains(needle) == true } ?: true
        val valOk = query.valueContains?.let { needle -> msg.value?.contains(needle) == true } ?: true
        return keyOk && valOk
    }

    private fun nextCursorFor(
        consumer: KafkaConsumer<ByteArray, ByteArray>,
        tps: List<TopicPartition>,
        end: Map<TopicPartition, Long>,
        mode: BrowseMode,
    ): Map<Int, Long>? {
        if (mode == BrowseMode.LATEST) return null
        val cursor = HashMap<Int, Long>(tps.size)
        var hasMore = false
        for (tp in tps) {
            val pos = runCatching { consumer.position(tp, Duration.ofSeconds(2)) }.getOrNull() ?: continue
            val endOff = end[tp] ?: pos
            cursor[tp.partition()] = pos
            if (pos < endOff) hasMore = true
        }
        return if (hasMore) cursor else null
    }

    private fun allCaughtUp(
        consumer: KafkaConsumer<ByteArray, ByteArray>,
        tps: List<TopicPartition>,
        end: Map<TopicPartition, Long>,
    ): Boolean = tps.all { tp ->
        val pos = runCatching { consumer.position(tp, Duration.ofSeconds(2)) }.getOrNull()
        pos != null && pos >= (end[tp] ?: pos)
    }

    private fun toMessage(record: ConsumerRecord<ByteArray, ByteArray>): MessageRecord = MessageRecord(
        topic = record.topic(),
        partition = record.partition(),
        offset = record.offset(),
        timestamp = Instant.ofEpochMilli(record.timestamp()),
        key = record.key()?.toString(StandardCharsets.UTF_8),
        value = record.value()?.toString(StandardCharsets.UTF_8),
        headers = record.headers().associate { h ->
            h.key() to (h.value()?.toString(StandardCharsets.UTF_8) ?: "")
        },
    )

    private fun emptyPage(durationMs: Long) = BrowsePage(
        messages = emptyList(),
        nextCursor = null,
        hasMore = false,
        partitionsScanned = emptyList(),
        durationMs = durationMs,
    )

    private fun elapsed(started: Long) = (System.nanoTime() - started) / 1_000_000
}
