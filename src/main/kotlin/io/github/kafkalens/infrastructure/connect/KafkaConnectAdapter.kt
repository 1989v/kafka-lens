package io.github.kafkalens.infrastructure.connect

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.kafkalens.domain.connect.ConnectorDetail
import io.github.kafkalens.domain.connect.ConnectorSummary
import io.github.kafkalens.domain.connect.ConnectorType
import io.github.kafkalens.domain.connect.TaskSummary
import io.github.kafkalens.domain.ports.ClusterRegistry
import io.github.kafkalens.domain.ports.ConnectNotConfigured
import io.github.kafkalens.domain.ports.KafkaConnectPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

@Component
class KafkaConnectAdapter(
    private val registry: ClusterRegistry,
    private val objectMapper: ObjectMapper,
) : KafkaConnectPort {

    private val clients = ConcurrentHashMap<String, RestClient>()

    private fun client(clusterId: String): RestClient {
        val cfg = registry.require(clusterId)
        val url = cfg.connectUrl ?: throw ConnectNotConfigured(clusterId)
        return clients.computeIfAbsent(url) { RestClient.builder().baseUrl(url).build() }
    }

    override fun listConnectors(clusterId: String): List<ConnectorSummary> {
        val c = client(clusterId)
        val raw: Map<String, ExpandedConnector> = c.get()
            .uri("/connectors?expand=info&expand=status")
            .retrieve()
            .body(object : org.springframework.core.ParameterizedTypeReference<Map<String, ExpandedConnector>>() {})
            ?: emptyMap()

        return raw.values.map { expanded ->
            val infoCfg = expanded.info?.config ?: emptyMap()
            val type = when ((expanded.info?.type ?: expanded.status?.type)?.lowercase()) {
                "source" -> ConnectorType.SOURCE
                "sink" -> ConnectorType.SINK
                else -> ConnectorType.UNKNOWN
            }
            // For sinks, "topics" config; for sources, harder to know without DESCRIBE — skip.
            val topics = (infoCfg["topics"] ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            ConnectorSummary(
                name = expanded.info?.name ?: expanded.status?.name ?: "?",
                type = type,
                connectorClass = infoCfg["connector.class"],
                state = expanded.status?.connector?.state ?: "UNKNOWN",
                workerId = expanded.status?.connector?.worker_id,
                tasks = (expanded.status?.tasks ?: emptyList()).map { t ->
                    TaskSummary(
                        id = t.id,
                        state = t.state,
                        workerId = t.worker_id,
                        trace = t.trace,
                    )
                },
                topics = topics,
            )
        }.sortedBy { it.name }
    }

    override fun getConnector(clusterId: String, name: String): ConnectorDetail? {
        val c = client(clusterId)
        val info = runCatching {
            c.get().uri("/connectors/{n}", name).retrieve().body(ConnectInfo::class.java)
        }.getOrNull() ?: return null
        val status = runCatching {
            c.get().uri("/connectors/{n}/status", name).retrieve().body(ConnectStatus::class.java)
        }.getOrNull()

        val type = when ((info.type ?: status?.type)?.lowercase()) {
            "source" -> ConnectorType.SOURCE
            "sink" -> ConnectorType.SINK
            else -> ConnectorType.UNKNOWN
        }
        val topics = (info.config["topics"] ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }

        return ConnectorDetail(
            summary = ConnectorSummary(
                name = info.name,
                type = type,
                connectorClass = info.config["connector.class"],
                state = status?.connector?.state ?: "UNKNOWN",
                workerId = status?.connector?.worker_id,
                tasks = (status?.tasks ?: emptyList()).map { t ->
                    TaskSummary(t.id, t.state, t.worker_id, t.trace)
                },
                topics = topics,
            ),
            config = info.config,
        )
    }

    override fun restartConnector(clusterId: String, name: String, includeTasks: Boolean, onlyFailed: Boolean) {
        client(clusterId).post()
            .uri("/connectors/{n}/restart?includeTasks={t}&onlyFailed={f}", name, includeTasks, onlyFailed)
            .retrieve().toBodilessEntity()
    }

    override fun restartTask(clusterId: String, name: String, taskId: Int) {
        client(clusterId).post().uri("/connectors/{n}/tasks/{id}/restart", name, taskId)
            .retrieve().toBodilessEntity()
    }

    override fun pauseConnector(clusterId: String, name: String) {
        client(clusterId).put().uri("/connectors/{n}/pause", name).retrieve().toBodilessEntity()
    }

    override fun resumeConnector(clusterId: String, name: String) {
        client(clusterId).put().uri("/connectors/{n}/resume", name).retrieve().toBodilessEntity()
    }

    override fun deleteConnector(clusterId: String, name: String) {
        client(clusterId).delete().uri("/connectors/{n}", name).retrieve().toBodilessEntity()
    }

    // ---- DTOs that map the Connect REST shape ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ExpandedConnector(val info: ConnectInfo? = null, val status: ConnectStatus? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ConnectInfo(
        val name: String = "",
        val type: String? = null,
        val config: Map<String, String> = emptyMap(),
        val tasks: List<TaskRef> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TaskRef(val connector: String = "", val task: Int = 0)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ConnectStatus(
        val name: String = "",
        val type: String? = null,
        val connector: ConnectorState? = null,
        val tasks: List<TaskState> = emptyList(),
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ConnectorState(val state: String = "UNKNOWN", val worker_id: String? = null, val trace: String? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TaskState(val id: Int = 0, val state: String = "UNKNOWN", val worker_id: String? = null, val trace: String? = null)
}
