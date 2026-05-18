package io.github.kafkalens.domain.topic

data class ConsumerGroupMetadata(
    val clusterId: String,
    val groupId: String,
    val state: String,
    val members: List<MemberInfo>,
    val offsets: List<GroupOffset>,
) {
    val totalLag: Long get() = offsets.sumOf { it.lag.coerceAtLeast(0) }
}

data class MemberInfo(
    val memberId: String,
    val clientId: String,
    val host: String,
    val assignedPartitions: List<TopicPartitionRef>,
)

data class TopicPartitionRef(val topic: String, val partition: Int)

data class GroupOffset(
    val topic: String,
    val partition: Int,
    val currentOffset: Long,
    val endOffset: Long,
) {
    val lag: Long get() = (endOffset - currentOffset).coerceAtLeast(0)
}
