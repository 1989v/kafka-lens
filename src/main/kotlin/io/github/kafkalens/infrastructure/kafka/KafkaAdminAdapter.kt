package io.github.kafkalens.infrastructure.kafka

import io.github.kafkalens.domain.cluster.BrokerFeatureMatrix
import io.github.kafkalens.domain.cluster.BrokerFeatureMatrix.Feature
import io.github.kafkalens.domain.ports.BrokerInfo
import io.github.kafkalens.domain.ports.KafkaAdminPort
import io.github.kafkalens.domain.ports.TopicConfigEntry
import org.apache.kafka.clients.admin.AlterConfigOp
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.NewPartitions
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.ConfigResource
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
        val listings = runCatching { admin.listConsumerGroups().all().get() }
            .onFailure { log.warn(it) { "listConsumerGroups failed for $clusterId" } }
            .getOrElse { return emptyList() }
        val ids = listings.map { it.groupId() }
        if (ids.isEmpty()) return emptyList()

        // Batch 1: describe everyone at once
        val descriptions = runCatching { admin.describeConsumerGroups(ids).all().get() }
            .onFailure { log.warn(it) { "describeConsumerGroups batch failed" } }
            .getOrDefault(emptyMap())

        // Batch 2: pull each group's committed offsets in parallel virtual threads.
        // The Map-based listConsumerGroupOffsets exists in kafka-clients but its
        // batching is bounded broker-side; fanning out across virtual threads
        // turns out to be the most consistent across broker versions.
        val offsetsByGroup: Map<String, Map<TopicPartition, OffsetAndMetadata>> =
            ids.parallelStream().collect(
                java.util.stream.Collectors.toMap(
                    { it },
                    { gid ->
                        runCatching {
                            admin.listConsumerGroupOffsets(gid)
                                .partitionsToOffsetAndMetadata()
                                .get()
                                .filterValues { it != null }
                                .mapValues { it.value!! }
                        }.getOrDefault(emptyMap())
                    },
                ),
            )

        // Batch 3: single listOffsets call for every distinct topic-partition.
        val allTps = offsetsByGroup.values.flatMap { it.keys }.toSet()
        val endOffsets: Map<TopicPartition, Long> = if (allTps.isEmpty()) emptyMap() else
            runCatching {
                admin.listOffsets(allTps.associateWith { OffsetSpec.latest() })
                    .all()
                    .get()
                    .mapValues { it.value.offset() }
            }.getOrDefault(emptyMap())

        return ids.mapNotNull { gid ->
            val desc = descriptions[gid] ?: return@mapNotNull null
            val offsets = offsetsByGroup[gid] ?: emptyMap()
            ConsumerGroupMetadata(
                clusterId = clusterId,
                groupId = gid,
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
                        endOffset = endOffsets[tp] ?: om.offset(),
                    )
                },
            )
        }
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

    override fun describeTopicConfigs(clusterId: String, topic: String): List<TopicConfigEntry> {
        val admin = factory.admin(clusterId)
        val resource = ConfigResource(ConfigResource.Type.TOPIC, topic)
        val config = runCatching { admin.describeConfigs(listOf(resource)).all().get()[resource] }
            .onFailure { log.warn(it) { "describeConfigs failed for $clusterId/$topic" } }
            .getOrNull()
            ?: return emptyList()
        return config.entries().map { e: ConfigEntry ->
            TopicConfigEntry(
                name = e.name(),
                value = if (e.isSensitive) null else e.value(),
                source = e.source().name,
                isDefault = e.source() == ConfigEntry.ConfigSource.DEFAULT_CONFIG,
                readOnly = e.isReadOnly,
                sensitive = e.isSensitive,
                documentation = e.documentation(),
            )
        }.sortedBy { it.name }
    }

    override fun createTopic(
        clusterId: String,
        name: String,
        numPartitions: Int,
        replicationFactor: Short,
        configs: Map<String, String>,
    ) {
        val admin = factory.admin(clusterId)
        val newTopic = NewTopic(name, numPartitions, replicationFactor).configs(configs)
        admin.createTopics(listOf(newTopic)).all().get()
    }

    override fun deleteTopic(clusterId: String, name: String) {
        val admin = factory.admin(clusterId)
        admin.deleteTopics(listOf(name)).all().get()
    }

    override fun addPartitions(clusterId: String, name: String, totalPartitions: Int) {
        val admin = factory.admin(clusterId)
        admin.createPartitions(mapOf(name to NewPartitions.increaseTo(totalPartitions))).all().get()
    }

    override fun alterTopicConfigs(clusterId: String, name: String, entries: Map<String, String?>) {
        val admin = factory.admin(clusterId)
        val resource = ConfigResource(ConfigResource.Type.TOPIC, name)
        val ops = entries.map { (k, v) ->
            if (v == null) AlterConfigOp(ConfigEntry(k, ""), AlterConfigOp.OpType.DELETE)
            else AlterConfigOp(ConfigEntry(k, v), AlterConfigOp.OpType.SET)
        }
        admin.incrementalAlterConfigs(mapOf(resource to ops)).all().get()
    }

    override fun listBrokers(clusterId: String): List<BrokerInfo> {
        val admin = factory.admin(clusterId)
        val clusterDesc = admin.describeCluster()
        val nodes = clusterDesc.nodes().get()
        val controllerId = runCatching { clusterDesc.controller().get()?.id() }.getOrNull()

        // Aggregate partition load per broker from a fresh topic listing. This
        // costs an extra describeTopics roundtrip, but the brokers page is opt-in
        // so the cost only lands when the user navigates to it.
        val leaderCount = HashMap<Int, Int>(nodes.size)
        val replicaCount = HashMap<Int, Int>(nodes.size)
        runCatching {
            val topicNames = admin.listTopics().names().get().filter { !it.startsWith("_") }
            if (topicNames.isNotEmpty()) {
                val descs = admin.describeTopics(topicNames).allTopicNames().get()
                descs.values.forEach { td ->
                    td.partitions().forEach { p ->
                        p.leader()?.id()?.let { leaderId -> leaderCount.merge(leaderId, 1, Int::plus) }
                        p.replicas().forEach { r -> replicaCount.merge(r.id(), 1, Int::plus) }
                    }
                }
            }
        }.onFailure { log.warn(it) { "partition load aggregation failed" } }

        return nodes.map { n ->
            BrokerInfo(
                id = n.id(),
                host = n.host(),
                port = n.port(),
                rack = n.rack(),
                leaderPartitions = leaderCount[n.id()] ?: 0,
                totalReplicas = replicaCount[n.id()] ?: 0,
                isController = controllerId != null && n.id() == controllerId,
            )
        }.sortedBy { it.id }
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
