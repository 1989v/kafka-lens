package io.github.kafkalens.application.topic

import io.github.kafkalens.domain.ports.KafkaAdminPort
import io.github.kafkalens.domain.topic.TopicMetadata
import org.springframework.stereotype.Service

@Service
class ListTopicsUseCase(private val admin: KafkaAdminPort) {
    fun execute(clusterId: String, includeInternal: Boolean = false): List<TopicMetadata> =
        admin.listTopics(clusterId, includeInternal).sortedBy { it.name }
}
