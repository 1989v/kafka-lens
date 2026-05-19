package io.github.kafkalens.presentation.rest

import io.github.kafkalens.application.dashboard.ClusterDashboard
import io.github.kafkalens.application.dashboard.ClusterDashboardUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/clusters/{clusterId}")
class DashboardController(private val dashboard: ClusterDashboardUseCase) {

    @GetMapping("/dashboard")
    fun get(
        @PathVariable clusterId: String,
        @RequestParam(defaultValue = "false") includeInternal: Boolean,
    ): ClusterDashboard = dashboard.execute(clusterId, includeInternal)
}
