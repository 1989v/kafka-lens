package io.github.kafkalens.presentation.rest

import io.github.kafkalens.domain.ports.ClusterRegistry
import io.github.kafkalens.domain.ports.KafkaAdminPort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/clusters")
class ClusterController(
    private val registry: ClusterRegistry,
    private val admin: KafkaAdminPort,
) {
    @GetMapping
    fun list(): List<ClusterSummary> = registry.list().map {
        ClusterSummary(id = it.id, name = it.name, bootstrapServers = it.bootstrapServers)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ClusterDetail {
        val cfg = registry.require(id)
        val features = runCatching { admin.brokerFeatures(id) }.getOrNull()
        return ClusterDetail(
            id = cfg.id,
            name = cfg.name,
            bootstrapServers = cfg.bootstrapServers,
            brokerVersion = features?.brokerVersion,
            brokerCount = features?.brokerCount ?: 0,
            supportedFeatures = features?.supports?.mapKeys { it.key.name } ?: emptyMap(),
            dlqNamingPatterns = cfg.dlqNamingPatterns,
        )
    }

    data class ClusterSummary(val id: String, val name: String, val bootstrapServers: String)
    data class ClusterDetail(
        val id: String,
        val name: String,
        val bootstrapServers: String,
        val brokerVersion: String?,
        val brokerCount: Int,
        val supportedFeatures: Map<String, Boolean>,
        val dlqNamingPatterns: List<String>,
    )
}
