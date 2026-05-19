package io.github.kafkalens.application.topic

import io.github.kafkalens.domain.ports.KafkaAdminPort
import io.github.kafkalens.domain.ports.TopicConfigEntry
import org.springframework.stereotype.Service

@Service
class GetTopicConfigsUseCase(private val admin: KafkaAdminPort) {
    fun execute(clusterId: String, topic: String): List<TopicConfigEntry> =
        admin.describeTopicConfigs(clusterId, topic)
}
