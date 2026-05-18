package io.github.kafkalens.domain.message

import java.time.Instant

data class MessageRecord(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val timestamp: Instant,
    val key: String?,
    val value: String?,
    val headers: Map<String, String>,
) {
    val fingerprint: String get() = "$topic:$partition:$offset"
}
