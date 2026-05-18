package io.github.kafkalens.infrastructure.dlq

import io.github.kafkalens.domain.dlq.DlqMessage
import io.github.kafkalens.domain.message.MessageRecord
import io.github.kafkalens.domain.ports.DlqReaderPort
import io.github.kafkalens.infrastructure.kafka.KafkaClientFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class DlqReaderAdapter(private val factory: KafkaClientFactory) : DlqReaderPort {

    /**
     * Read a page of DLQ messages. We pull a small window using a transient consumer
     * and immediately close it — we are not maintaining a long-lived subscriber here.
     */
    override fun readDlqPage(
        clusterId: String,
        dlqTopic: String,
        partition: Int?,
        fromOffset: Long?,
        limit: Int,
    ): List<DlqMessage> {
        val out = ArrayList<DlqMessage>(limit)
        val groupId = "kafka-lens-dlq-${UUID.randomUUID().toString().take(8)}"
        factory.newConsumer(clusterId, groupId).use { consumer ->
            val partitions = consumer.partitionsFor(dlqTopic, Duration.ofSeconds(5))
                ?.map { it.partition() }
                ?.filter { partition == null || it == partition }
                ?: emptyList()
            if (partitions.isEmpty()) return emptyList()

            val tps = partitions.map { TopicPartition(dlqTopic, it) }
            consumer.assign(tps)
            if (fromOffset != null) tps.forEach { consumer.seek(it, fromOffset) }
            else consumer.seekToBeginning(tps)

            val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
            while (out.size < limit && System.nanoTime() < deadline) {
                val records = consumer.poll(Duration.ofMillis(300))
                if (records.isEmpty) break
                for (record in records) {
                    out += toDlqMessage(record)
                    if (out.size >= limit) break
                }
            }
        }
        return out
    }

    override fun readByFingerprint(clusterId: String, dlqTopic: String, partition: Int, offset: Long): DlqMessage? {
        val page = readDlqPage(clusterId, dlqTopic, partition, offset, limit = 1)
        return page.firstOrNull { it.record.partition == partition && it.record.offset == offset }
    }

    private fun toDlqMessage(record: ConsumerRecord<ByteArray, ByteArray>): DlqMessage {
        val headers = record.headers().associate { h ->
            h.key() to (h.value()?.toString(StandardCharsets.UTF_8) ?: "")
        }
        val msg = MessageRecord(
            topic = record.topic(),
            partition = record.partition(),
            offset = record.offset(),
            timestamp = Instant.ofEpochMilli(record.timestamp()),
            key = record.key()?.toString(StandardCharsets.UTF_8),
            value = record.value()?.toString(StandardCharsets.UTF_8),
            headers = headers,
        )
        return DlqMessage(
            record = msg,
            originTopic = headers[H_ORIG_TOPIC] ?: headers[H_DLT_ORIG_TOPIC],
            originPartition = (headers[H_ORIG_PARTITION] ?: headers[H_DLT_ORIG_PARTITION])?.toIntOrNull(),
            originOffset = (headers[H_ORIG_OFFSET] ?: headers[H_DLT_ORIG_OFFSET])?.toLongOrNull(),
            failureReason = headers[H_EXCEPTION_MESSAGE] ?: headers[H_DLT_EXCEPTION_MESSAGE],
            exceptionClass = headers[H_EXCEPTION_FQCN] ?: headers[H_DLT_EXCEPTION_FQCN],
            stacktrace = headers[H_EXCEPTION_STACKTRACE] ?: headers[H_DLT_EXCEPTION_STACKTRACE],
            retryCount = headers["x-retry-count"]?.toIntOrNull(),
            lastAttemptAt = headers["x-last-attempt-at"]?.let { runCatching { Instant.parse(it) }.getOrNull() },
            correlationId = headers["correlation-id"] ?: headers["x-correlation-id"] ?: headers["trace-id"],
        )
    }

    companion object {
        // Spring Kafka DLT headers
        const val H_DLT_ORIG_TOPIC = "kafka_dlt-original-topic"
        const val H_DLT_ORIG_PARTITION = "kafka_dlt-original-partition"
        const val H_DLT_ORIG_OFFSET = "kafka_dlt-original-offset"
        const val H_DLT_EXCEPTION_FQCN = "kafka_dlt-exception-fqcn"
        const val H_DLT_EXCEPTION_MESSAGE = "kafka_dlt-exception-message"
        const val H_DLT_EXCEPTION_STACKTRACE = "kafka_dlt-exception-stacktrace"

        // Plain alternative naming (kafka-clients without Spring)
        const val H_ORIG_TOPIC = "x-original-topic"
        const val H_ORIG_PARTITION = "x-original-partition"
        const val H_ORIG_OFFSET = "x-original-offset"
        const val H_EXCEPTION_FQCN = "x-exception-class"
        const val H_EXCEPTION_MESSAGE = "x-exception-message"
        const val H_EXCEPTION_STACKTRACE = "x-exception-stacktrace"
    }
}
