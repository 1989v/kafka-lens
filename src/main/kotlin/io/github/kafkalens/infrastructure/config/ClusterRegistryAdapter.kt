package io.github.kafkalens.infrastructure.config

import io.github.kafkalens.domain.cluster.ClusterConfig
import io.github.kafkalens.domain.ports.ClusterRegistry
import org.springframework.stereotype.Component

@Component
class ClusterRegistryAdapter(props: KafkaLensProperties) : ClusterRegistry {
    private val byId: Map<String, ClusterConfig> = props.clusters
        .filter { it.id.isNotBlank() && it.bootstrapServers.isNotBlank() }
        .associate { it.id to it.toDomain() }

    override fun list(): List<ClusterConfig> = byId.values.toList()

    override fun get(id: String): ClusterConfig? = byId[id]
}
