package io.github.kafkalens.infrastructure.kafka

import io.github.kafkalens.domain.cluster.BrokerFeatureMatrix
import io.github.kafkalens.domain.cluster.BrokerFeatureMatrix.Feature
import io.github.kafkalens.domain.ports.KafkaAdminPort
import io.github.kafkalens.domain.topic.ConsumerGroupMetadata
import io.github.kafkalens.domain.topic.GroupOffset
import io.github.kafkalens.domain.topic.MemberInfo
import io.github.kafkalens.domain.topic.PartitionMetadata
import io.github.kafkalens.domain.topic.TopicMetadata
import io.github.kafkalens.domain.topic.TopicPartitionRef
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.OffsetSpec
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.springframework.stereotype.Component

private val log = KotlinLogging.logger {}

@Component
class KafkaAdminAdapter(private val factory: KafkaClientFactory) : KafkaAdminPort {

    override fun listTopics(clusterId: String, includeInternal: Boolean): List<TopicMetadata> {
        val admin = factory.admin(clusterId)
        val names = admin.listTopics().names().get().filter { includeInternal || !it.startsWith("_") }
        if (names.isEmpty()) return emptyList()

        val descriptions = admin.describeTopics(names).allTopicNames().get()

        val tpsBeginning = mutableMapOf<TopicPartition, OffsetSpec>()
        val tpsEnd = mutableMapOf<TopicPartition, OffsetSpec>()
        descriptions.values.forEach { d ->
            d.partitions().forEach { p ->
                tpsBeginning[TopicPartition(d.name(), p.partition())] = OffsetSpec.earliest()
                tpsEnd[TopicPartition(d.name(), p.partition())] = OffsetSpec.latest()
            }
        }
        val beginning = admin.listOffsets(tpsBeginning).all().get()
        val end = admin.listOffsets(tpsEnd).all().get()

        return descriptions.values.map { d ->
            val partitions = d.partitions().map { p ->
                val tp = TopicPartition(d.name(), p.partition())
                PartitionMetadata(
                    partition = p.partition(),
                    leader = p.leader()?.id(),
                    replicas = p.replicas().map { it.id() },
                    inSyncReplicas = p.isr().map { it.id() },
                    beginningOffset = beginning[tp]?.offset() ?: 0L,
                    endOffset = end[tp]?.offset() ?: 0L,
                )
            }
            TopicMetadata(
                clusterId = clusterId,
                name = d.name(),
                partitions = partitions,
                internal = d.isInternal,
            )
        }
    }

    override fun getTopic(clusterId: String, name: String): TopicMetadata? =
        runCatching { listTopics(clusterId, includeInternal = true).firstOrNull { it.name == name } }
            .onFailure { log.warn(it) { "getTopic failed for $clusterId/$name" } }
            .getOrNull()

    override fun listConsumerGroups(clusterId: String): List<ConsumerGroupMetadata> {
        val admin = factory.admin(clusterId)
        val listings = admin.listConsumerGroups().all().get()
        val ids = listings.map { it.groupId() }
        if (ids.isEmpty()) return emptyList()
        return ids.mapNotNull { id -> describeGroup(admin, clusterId, id) }
    }

    override fun getConsumerGroup(clusterId: String, groupId: String): ConsumerGroupMetadata? =
        describeGroup(factory.admin(clusterId), clusterId, groupId)

    private fun describeGroup(admin: AdminClient, clusterId: String, groupId: String): ConsumerGroupMetadata? {
        val desc = runCatching { admin.describeConsumerGroups(listOf(groupId)).all().get()[groupId] }
            .getOrElse {
                log.warn(it) { "describeConsumerGroups failed for $groupId" }
                return null
            } ?: return null

        val offsets: Map<TopicPartition, OffsetAndMetadata> =
            admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get()
                .filterValues { it != null }
                .mapValues { it.value!! }

        val endRequest = offsets.keys.associateWith { OffsetSpec.latest() }
        val endOffsets = if (endRequest.isNotEmpty())
            admin.listOffsets(endRequest).all().get()
        else emptyMap()

        return ConsumerGroupMetadata(
            clusterId = clusterId,
            groupId = groupId,
            state = desc.state().toString(),
            members = desc.members().map { m ->
                MemberInfo(
                    memberId = m.consumerId(),
                    clientId = m.clientId(),
                    host = m.host(),
                    assignedPartitions = m.assignment().topicPartitions()
                        .map { TopicPartitionRef(it.topic(), it.partition()) },
                )
            },
            offsets = offsets.map { (tp, om) ->
                GroupOffset(
                    topic = tp.topic(),
                    partition = tp.partition(),
                    currentOffset = om.offset(),
                    endOffset = endOffsets[tp]?.offset() ?: om.offset(),
                )
            },
        )
    }

    override fun brokerFeatures(clusterId: String): BrokerFeatureMatrix {
        val admin = factory.admin(clusterId)
        val nodes = admin.describeCluster().nodes().get()
        val version = runCatching {
            val configs = admin.describeFeatures().featureMetadata().get()
            configs.finalizedFeatures()
                .mapValues { it.value.maxVersionLevel() }
                .entries.firstOrNull()?.let { "${it.key}=${it.value}" }
        }.getOrNull()
        return BrokerFeatureMatrix(
            clusterId = clusterId,
            brokerVersion = version,
            brokerCount = nodes.size,
            kraftMode = null,
            supports = mapOf(
                Feature.INCREMENTAL_ALTER_CONFIGS to true,
                Feature.DESCRIBE_PRODUCERS to true,
                Feature.LIST_OFFSETS_BY_TIMESTAMP to true,
                Feature.CONSUMER_GROUP_DESCRIBE_V2 to false,
                Feature.ALTER_CONSUMER_GROUP_OFFSETS to true,
            ),
        )
    }
}
