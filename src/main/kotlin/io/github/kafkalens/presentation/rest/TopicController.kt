package io.github.kafkalens.presentation.rest

import io.github.kafkalens.application.topic.ListConsumerGroupsUseCase
import io.github.kafkalens.application.topic.ListTopicsUseCase
import io.github.kafkalens.domain.ports.KafkaAdminPort
import io.github.kafkalens.domain.topic.ConsumerGroupMetadata
import io.github.kafkalens.domain.topic.TopicMetadata
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

    @GetMapping("/consumer-groups")
    fun consumerGroups(@PathVariable clusterId: String): List<ConsumerGroupMetadata> =
        listConsumerGroups.execute(clusterId)

    @GetMapping("/consumer-groups/{groupId}")
    fun consumerGroup(@PathVariable clusterId: String, @PathVariable groupId: String): ConsumerGroupMetadata? =
        admin.getConsumerGroup(clusterId, groupId)
}
