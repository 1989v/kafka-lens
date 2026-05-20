package io.github.kafkalens.infrastructure.schemaregistry

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.kafkalens.domain.ports.ClusterRegistry
import io.github.kafkalens.domain.ports.SchemaRegistryPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

@Component
class SchemaRegistryAdapter(private val registry: ClusterRegistry) : SchemaRegistryPort {

    private val clients = ConcurrentHashMap<String, RestClient>()
    // (clusterId, schemaId) -> avro schema json
    private val cache = ConcurrentHashMap<Pair<String, Int>, String>()

    override fun fetchSchema(clusterId: String, schemaId: Int): String? {
        cache[clusterId to schemaId]?.let { return it }
        val cfg = registry.get(clusterId) ?: return null
        val url = cfg.schemaRegistryUrl ?: return null
        val client = clients.computeIfAbsent(url) { RestClient.builder().baseUrl(url).build() }

        val body: SchemaResponse? = runCatching {
            client.get().uri("/schemas/ids/{id}", schemaId).retrieve().body(SchemaResponse::class.java)
        }.onFailure { log.debug { "SR fetch failed for id=$schemaId: ${it.message}" } }.getOrNull()

        val schema = body?.schema?.takeIf { it.isNotBlank() } ?: return null
        cache[clusterId to schemaId] = schema
        return schema
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SchemaResponse(val schema: String? = null, val schemaType: String? = null)
}
