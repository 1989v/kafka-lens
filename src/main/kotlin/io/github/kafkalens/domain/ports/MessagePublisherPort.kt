package io.github.kafkalens.domain.ports

interface MessagePublisherPort {
    /**
     * Publish a single message to the specified topic. Implementations MUST refuse
     * publication to any topic that is registered as a DLQ — direct DLQ publishing is
     * forbidden by Kafka Lens design (only reprocess flows may write to DLQ targets).
     */
    fun publish(
        clusterId: String,
        topic: String,
        key: String?,
        value: String,
        headers: Map<String, String> = emptyMap(),
    ): PublishResult
}

data class PublishResult(
    val partition: Int,
    val offset: Long,
    val timestampMs: Long,
)
