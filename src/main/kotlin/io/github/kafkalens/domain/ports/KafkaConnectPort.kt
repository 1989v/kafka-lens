package io.github.kafkalens.domain.ports

import io.github.kafkalens.domain.connect.ConnectorDetail
import io.github.kafkalens.domain.connect.ConnectorSummary

/**
 * Talks to a single Kafka Connect REST endpoint (one per cluster). All methods
 * throw [ConnectNotConfigured] if the target cluster has no `connectUrl` set.
 */
interface KafkaConnectPort {
    fun listConnectors(clusterId: String): List<ConnectorSummary>
    fun getConnector(clusterId: String, name: String): ConnectorDetail?
    fun restartConnector(clusterId: String, name: String, includeTasks: Boolean = true, onlyFailed: Boolean = false)
    fun restartTask(clusterId: String, name: String, taskId: Int)
    fun pauseConnector(clusterId: String, name: String)
    fun resumeConnector(clusterId: String, name: String)
    fun deleteConnector(clusterId: String, name: String)
}

class ConnectNotConfigured(clusterId: String) :
    IllegalStateException("Cluster '$clusterId' has no connectUrl configured. Set CLUSTERS_${clusterId.uppercase()}_CONNECTURL or the connectUrl field in YAML.")
