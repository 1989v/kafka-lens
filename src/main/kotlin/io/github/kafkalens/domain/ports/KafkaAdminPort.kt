package io.github.kafkalens.domain.ports

import io.github.kafkalens.domain.cluster.BrokerFeatureMatrix
import io.github.kafkalens.domain.topic.ConsumerGroupMetadata
import io.github.kafkalens.domain.topic.TopicMetadata

/**
 * Admin-side operations against a single Kafka cluster. Implementations are
 * expected to multiplex over a per-cluster Kafka AdminClient instance.
 */
interface KafkaAdminPort {
    fun listTopics(clusterId: String, includeInternal: Boolean = false): List<TopicMetadata>
    fun getTopic(clusterId: String, name: String): TopicMetadata?
    fun listConsumerGroups(clusterId: String): List<ConsumerGroupMetadata>
    fun getConsumerGroup(clusterId: String, groupId: String): ConsumerGroupMetadata?
    fun brokerFeatures(clusterId: String): BrokerFeatureMatrix
    fun describeTopicConfigs(clusterId: String, topic: String): List<TopicConfigEntry>
    fun listBrokers(clusterId: String): List<BrokerInfo>

    fun createTopic(
        clusterId: String,
        name: String,
        numPartitions: Int,
        replicationFactor: Short,
        configs: Map<String, String> = emptyMap(),
    )
    fun deleteTopic(clusterId: String, name: String)
    fun addPartitions(clusterId: String, name: String, totalPartitions: Int)
    /**
     * For each entry: a non-null value sets the config; a null value resets it
     * to the broker default.
     */
    fun alterTopicConfigs(clusterId: String, name: String, entries: Map<String, String?>)
}

data class BrokerInfo(
    val id: Int,
    val host: String,
    val port: Int,
    val rack: String?,
    val leaderPartitions: Int,
    val totalReplicas: Int,
    val isController: Boolean,
)

data class TopicConfigEntry(
    val name: String,
    val value: String?,
    val source: String,
    val isDefault: Boolean,
    val readOnly: Boolean,
    val sensitive: Boolean,
    val documentation: String? = null,
)
