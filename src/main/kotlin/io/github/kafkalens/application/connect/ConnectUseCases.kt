package io.github.kafkalens.application.connect

import io.github.kafkalens.domain.connect.ConnectorDetail
import io.github.kafkalens.domain.connect.ConnectorSummary
import io.github.kafkalens.domain.ports.KafkaConnectPort
import org.springframework.stereotype.Service

@Service
class ListConnectorsUseCase(private val connect: KafkaConnectPort) {
    fun execute(clusterId: String): List<ConnectorSummary> = connect.listConnectors(clusterId)
}

@Service
class GetConnectorUseCase(private val connect: KafkaConnectPort) {
    fun execute(clusterId: String, name: String): ConnectorDetail? = connect.getConnector(clusterId, name)
}

@Service
class ConnectorActionsUseCase(private val connect: KafkaConnectPort) {
    fun restart(clusterId: String, name: String, includeTasks: Boolean, onlyFailed: Boolean) =
        connect.restartConnector(clusterId, name, includeTasks, onlyFailed)

    fun restartTask(clusterId: String, name: String, taskId: Int) =
        connect.restartTask(clusterId, name, taskId)

    fun pause(clusterId: String, name: String) = connect.pauseConnector(clusterId, name)
    fun resume(clusterId: String, name: String) = connect.resumeConnector(clusterId, name)
    fun delete(clusterId: String, name: String) = connect.deleteConnector(clusterId, name)
}
