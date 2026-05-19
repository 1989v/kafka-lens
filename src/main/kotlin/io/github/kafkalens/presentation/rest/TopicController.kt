package io.github.kafkalens.presentation.rest

import io.github.kafkalens.application.topic.AddPartitionsUseCase
import io.github.kafkalens.application.topic.AlterTopicConfigsUseCase
import io.github.kafkalens.application.topic.CreateTopicUseCase
import io.github.kafkalens.application.topic.DeleteTopicUseCase
import io.github.kafkalens.application.topic.GetTopicConfigsUseCase
import io.github.kafkalens.application.topic.ListConsumerGroupsUseCase
import io.github.kafkalens.application.topic.ListTopicsUseCase
import io.github.kafkalens.domain.ports.KafkaAdminPort
import io.github.kafkalens.domain.ports.TopicConfigEntry
import io.github.kafkalens.domain.topic.ConsumerGroupMetadata
import io.github.kafkalens.domain.topic.TopicMetadata
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/clusters/{clusterId}")
class TopicController(
    private val listTopics: ListTopicsUseCase,
    private val listConsumerGroups: ListConsumerGroupsUseCase,
    private val getTopicConfigs: GetTopicConfigsUseCase,
    private val createTopic: CreateTopicUseCase,
    private val deleteTopic: DeleteTopicUseCase,
    private val addPartitions: AddPartitionsUseCase,
    private val alterTopicConfigs: AlterTopicConfigsUseCase,
    private val admin: KafkaAdminPort,
) {
    @GetMapping("/topics")
    fun topics(
        @PathVariable clusterId: String,
        @RequestParam(defaultValue = "false") includeInternal: Boolean,
    ): List<TopicMetadata> = listTopics.execute(clusterId, includeInternal)

    @GetMapping("/topics/{name}")
    fun topic(@PathVariable clusterId: String, @PathVariable name: String): TopicMetadata? =
        admin.getTopic(clusterId, name)

    @GetMapping("/topics/{name}/configs")
    fun configs(@PathVariable clusterId: String, @PathVariable name: String): List<TopicConfigEntry> =
        getTopicConfigs.execute(clusterId, name)

    @PostMapping("/topics")
    fun create(
        @PathVariable clusterId: String,
        @RequestBody req: CreateTopicRequest,
    ) {
        createTopic.execute(
            clusterId = clusterId,
            name = req.name,
            numPartitions = req.numPartitions,
            replicationFactor = req.replicationFactor.toShort(),
            configs = req.configs,
        )
    }

    @DeleteMapping("/topics/{name}")
    fun delete(@PathVariable clusterId: String, @PathVariable name: String) {
        deleteTopic.execute(clusterId, name)
    }

    @PostMapping("/topics/{name}/partitions")
    fun grow(
        @PathVariable clusterId: String,
        @PathVariable name: String,
        @RequestBody req: AddPartitionsRequest,
    ) {
        addPartitions.execute(clusterId, name, req.totalPartitions)
    }

    @PutMapping("/topics/{name}/configs")
    fun alterConfigs(
        @PathVariable clusterId: String,
        @PathVariable name: String,
        @RequestBody req: AlterConfigsRequest,
    ) {
        alterTopicConfigs.execute(clusterId, name, req.entries)
    }

    data class CreateTopicRequest(
        val name: String,
        val numPartitions: Int = 1,
        val replicationFactor: Int = 1,
        val configs: Map<String, String> = emptyMap(),
    )
    data class AddPartitionsRequest(val totalPartitions: Int)
    data class AlterConfigsRequest(val entries: Map<String, String?>)

    @GetMapping("/consumer-groups")
    fun consumerGroups(@PathVariable clusterId: String): List<ConsumerGroupMetadata> =
        listConsumerGroups.execute(clusterId)

    @GetMapping("/consumer-groups/{groupId}")
    fun consumerGroup(@PathVariable clusterId: String, @PathVariable groupId: String): ConsumerGroupMetadata? =
        admin.getConsumerGroup(clusterId, groupId)
}
