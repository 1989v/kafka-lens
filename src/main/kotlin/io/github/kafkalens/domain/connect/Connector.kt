package io.github.kafkalens.domain.connect

enum class ConnectorType { SOURCE, SINK, UNKNOWN }

data class TaskSummary(
    val id: Int,
    val state: String,
    val workerId: String?,
    val trace: String?,
)

data class ConnectorSummary(
    val name: String,
    val type: ConnectorType,
    val connectorClass: String?,
    val state: String,
    val workerId: String?,
    val tasks: List<TaskSummary>,
    val topics: List<String>,
) {
    val failedTasks: Int get() = tasks.count { it.state == "FAILED" }
    val runningTasks: Int get() = tasks.count { it.state == "RUNNING" }
}

data class ConnectorDetail(
    val summary: ConnectorSummary,
    val config: Map<String, String>,
)
