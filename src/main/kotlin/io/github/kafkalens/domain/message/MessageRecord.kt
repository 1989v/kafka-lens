package io.github.kafkalens.domain.message

import java.time.Instant

/**
 * A consumed Kafka record presented to the UI. `value` is already decoded —
 * for plain JSON/text topics it's the UTF-8 string, for Confluent Avro
 * topics it's the Avro→JSON projection. `encoding` and `schemaId` let the
 * FE show a badge and label which schema was used.
 */
data class MessageRecord(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val timestamp: Instant,
    val key: String?,
    val value: String?,
    val headers: Map<String, String>,
    val encoding: ValueEncoding = ValueEncoding.UTF8,
    val schemaId: Int? = null,
) {
    val fingerprint: String get() = "$topic:$partition:$offset"
}

enum class ValueEncoding { UTF8, AVRO, FALLBACK_BASE64 }
