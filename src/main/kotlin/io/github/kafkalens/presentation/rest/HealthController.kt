package io.github.kafkalens.presentation.rest

import io.github.kafkalens.domain.ports.ClusterRegistry
import io.github.kafkalens.infrastructure.config.KafkaLensProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class HealthController(
    private val registry: ClusterRegistry,
    private val props: KafkaLensProperties,
) {

    @GetMapping("/info")
    fun info(): Info = Info(
        product = "kafka-lens",
        version = javaClass.`package`?.implementationVersion ?: "dev",
        clusterCount = registry.list().size,
        features = Features(
            allowDestructiveTopicOps = props.topicOps.allowDestructive,
        ),
    )

    data class Info(
        val product: String,
        val version: String,
        val clusterCount: Int,
        val features: Features,
    )
    data class Features(val allowDestructiveTopicOps: Boolean)
}
