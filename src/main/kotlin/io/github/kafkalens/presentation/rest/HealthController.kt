package io.github.kafkalens.presentation.rest

import io.github.kafkalens.domain.ports.ClusterRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HealthController(private val registry: ClusterRegistry) {

    @GetMapping("/info")
    fun info(): Info = Info(
        product = "kafka-lens",
        version = javaClass.`package`?.implementationVersion ?: "dev",
        clusterCount = registry.list().size,
    )

    data class Info(val product: String, val version: String, val clusterCount: Int)
}
