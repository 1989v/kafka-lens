package io.github.kafkalens.domain.topic

data class TopicMetadata(
    val clusterId: String,
    val name: String,
    val partitions: List<PartitionMetadata>,
    val internal: Boolean,
    val configs: Map<String, String> = emptyMap(),
) {
    val totalMessages: Long get() = partitions.sumOf { (it.endOffset - it.beginningOffset).coerceAtLeast(0) }
}

data class PartitionMetadata(
    val partition: Int,
    val leader: Int?,
    val replicas: List<Int>,
    val inSyncReplicas: List<Int>,
    val beginningOffset: Long,
    val endOffset: Long,
)
