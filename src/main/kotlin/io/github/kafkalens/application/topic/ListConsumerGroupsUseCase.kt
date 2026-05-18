package io.github.kafkalens.application.topic

import io.github.kafkalens.domain.ports.KafkaAdminPort
import io.github.kafkalens.domain.topic.ConsumerGroupMetadata
import org.springframework.stereotype.Service

@Service
class ListConsumerGroupsUseCase(private val admin: KafkaAdminPort) {
    fun execute(clusterId: String): List<ConsumerGroupMetadata> =
        admin.listConsumerGroups(clusterId).sortedBy { it.groupId }
}
