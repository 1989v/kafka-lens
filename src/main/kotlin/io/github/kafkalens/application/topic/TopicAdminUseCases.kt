package io.github.kafkalens.application.topic

import io.github.kafkalens.domain.ports.KafkaAdminPort
import org.springframework.stereotype.Service

@Service
class CreateTopicUseCase(private val admin: KafkaAdminPort) {
    fun execute(
        clusterId: String,
        name: String,
        numPartitions: Int,
        replicationFactor: Short,
        configs: Map<String, String>,
    ) {
        require(name.isNotBlank()) { "topic name must not be blank" }
        require(numPartitions in 1..10_000) { "numPartitions must be in [1, 10000]" }
        require(replicationFactor in 1..10) { "replicationFactor must be in [1, 10]" }
        admin.createTopic(clusterId, name, numPartitions, replicationFactor, configs)
    }
}

@Service
class DeleteTopicUseCase(private val admin: KafkaAdminPort) {
    fun execute(clusterId: String, name: String) {
        require(name.isNotBlank()) { "topic name must not be blank" }
        admin.deleteTopic(clusterId, name)
    }
}

@Service
class AddPartitionsUseCase(private val admin: KafkaAdminPort) {
    fun execute(clusterId: String, name: String, totalPartitions: Int) {
        require(totalPartitions in 1..10_000) { "totalPartitions must be in [1, 10000]" }
        admin.addPartitions(clusterId, name, totalPartitions)
    }
}

@Service
class AlterTopicConfigsUseCase(private val admin: KafkaAdminPort) {
    fun execute(clusterId: String, name: String, entries: Map<String, String?>) {
        require(entries.isNotEmpty()) { "no configs to alter" }
        admin.alterTopicConfigs(clusterId, name, entries)
    }
}
