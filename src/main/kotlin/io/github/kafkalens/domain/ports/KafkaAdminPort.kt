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
}
