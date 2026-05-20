package io.github.kafkalens.presentation.rest

import io.github.kafkalens.application.connect.ConnectorActionsUseCase
import io.github.kafkalens.application.connect.GetConnectorUseCase
import io.github.kafkalens.application.connect.ListConnectorsUseCase
import io.github.kafkalens.domain.connect.ConnectorDetail
import io.github.kafkalens.domain.connect.ConnectorSummary
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/clusters/{clusterId}/connect")
class ConnectorsController(
    private val list: ListConnectorsUseCase,
    private val get: GetConnectorUseCase,
    private val actions: ConnectorActionsUseCase,
) {
    @GetMapping("/connectors")
    fun listConnectors(@PathVariable clusterId: String): List<ConnectorSummary> = list.execute(clusterId)

    @GetMapping("/connectors/{name}")
    fun getConnector(@PathVariable clusterId: String, @PathVariable name: String): ResponseEntity<ConnectorDetail> {
        val detail = get.execute(clusterId, name) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(detail)
    }

    @PostMapping("/connectors/{name}/restart")
    fun restart(
        @PathVariable clusterId: String,
        @PathVariable name: String,
        @RequestParam(defaultValue = "true") includeTasks: Boolean,
        @RequestParam(defaultValue = "false") onlyFailed: Boolean,
    ) = actions.restart(clusterId, name, includeTasks, onlyFailed)

    @PostMapping("/connectors/{name}/tasks/{taskId}/restart")
    fun restartTask(@PathVariable clusterId: String, @PathVariable name: String, @PathVariable taskId: Int) =
        actions.restartTask(clusterId, name, taskId)

    @PutMapping("/connectors/{name}/pause")
    fun pause(@PathVariable clusterId: String, @PathVariable name: String) = actions.pause(clusterId, name)

    @PutMapping("/connectors/{name}/resume")
    fun resume(@PathVariable clusterId: String, @PathVariable name: String) = actions.resume(clusterId, name)

    @DeleteMapping("/connectors/{name}")
    fun delete(@PathVariable clusterId: String, @PathVariable name: String) = actions.delete(clusterId, name)
}
