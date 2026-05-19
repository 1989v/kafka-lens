package io.github.kafkalens.application.dashboard

import io.github.kafkalens.domain.cluster.BrokerFeatureMatrix
import io.github.kafkalens.domain.ports.KafkaAdminPort
import org.springframework.stereotype.Service

/**
 * Aggregates the cheap-to-compute monitoring numbers that the dashboard
 * needs in a single round-trip: per-topic message counts, per-topic lag
 * (summed across all consumer groups consuming it), per-group totals.
 *
 * The numbers come from AdminClient calls only — no Kafka consume —
 * so this is safe to refresh on a short interval from the UI.
 */
@Service
class ClusterDashboardUseCase(private val admin: KafkaAdminPort) {

    fun execute(clusterId: String, includeInternal: Boolean = false): ClusterDashboard {
        val topics = admin.listTopics(clusterId, includeInternal)
        val groups = admin.listConsumerGroups(clusterId)
        val features = runCatching { admin.brokerFeatures(clusterId) }.getOrNull()

        val topicMessageCount = topics.associate { it.name to it.totalMessages }
        val topicLagAccumulator = HashMap<String, Long>(topics.size)
        val topicGroupAccumulator = HashMap<String, MutableSet<String>>(topics.size)

        for (g in groups) {
            for (offset in g.offsets) {
                topicLagAccumulator.merge(offset.topic, offset.lag) { a, b -> a + b }
                topicGroupAccumulator.getOrPut(offset.topic) { mutableSetOf() }.add(g.groupId)
            }
        }

        val topicStats = topics.map { t ->
            TopicStats(
                name = t.name,
                partitions = t.partitions.size,
                totalMessages = t.totalMessages,
                consumingGroups = topicGroupAccumulator[t.name]?.size ?: 0,
                totalLag = topicLagAccumulator[t.name] ?: 0L,
                internal = t.internal,
            )
        }.sortedByDescending { it.totalLag }

        val groupStats = groups.map { g ->
            GroupStats(
                groupId = g.groupId,
                state = g.state,
                members = g.members.size,
                topicCount = g.offsets.map { it.topic }.distinct().size,
                totalLag = g.totalLag,
            )
        }.sortedByDescending { it.totalLag }

        return ClusterDashboard(
            clusterId = clusterId,
            brokerCount = features?.brokerCount ?: 0,
            brokerVersion = features?.brokerVersion,
            supports = features?.supports ?: emptyMap(),
            topicCount = topics.size,
            internalTopicCount = topics.count { it.internal },
            totalMessages = topicMessageCount.values.sum(),
            consumerGroupCount = groups.size,
            totalLag = topicLagAccumulator.values.sum(),
            topicStats = topicStats,
            groupStats = groupStats,
        )
    }
}

data class ClusterDashboard(
    val clusterId: String,
    val brokerCount: Int,
    val brokerVersion: String?,
    val supports: Map<BrokerFeatureMatrix.Feature, Boolean>,
    val topicCount: Int,
    val internalTopicCount: Int,
    val totalMessages: Long,
    val consumerGroupCount: Int,
    val totalLag: Long,
    val topicStats: List<TopicStats>,
    val groupStats: List<GroupStats>,
)

data class TopicStats(
    val name: String,
    val partitions: Int,
    val totalMessages: Long,
    val consumingGroups: Int,
    val totalLag: Long,
    val internal: Boolean,
)

data class GroupStats(
    val groupId: String,
    val state: String,
    val members: Int,
    val topicCount: Int,
    val totalLag: Long,
)
