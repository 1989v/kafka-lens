package io.github.kafkalens.application.dlq

import io.github.kafkalens.domain.dlq.TopicDlqMapping
import io.github.kafkalens.domain.ports.ClusterRegistry
import io.github.kafkalens.domain.ports.DlqMappingPort
import io.github.kafkalens.domain.ports.KafkaAdminPort
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ListDlqMappingsUseCase(
    private val mappings: DlqMappingPort,
    private val autoDetect: AutoDetectDlqMappingsUseCase,
) {
    fun execute(clusterId: String, refresh: Boolean = false): List<TopicDlqMapping> {
        if (refresh) autoDetect.execute(clusterId)
        return mappings.list(clusterId)
    }
}

@Service
class AutoDetectDlqMappingsUseCase(
    private val registry: ClusterRegistry,
    private val admin: KafkaAdminPort,
    private val mappings: DlqMappingPort,
) {
    fun execute(clusterId: String): List<TopicDlqMapping> {
        val cfg = registry.require(clusterId)
        val topics = admin.listTopics(clusterId, includeInternal = false).map { it.name }.toSet()
        val patterns = cfg.dlqNamingPatterns
        val detected = mutableListOf<TopicDlqMapping>()
        val now = Instant.now()

        for (origin in topics) {
            for (pattern in patterns) {
                val candidate = pattern.replace("{topic}", origin)
                if (candidate != origin && candidate in topics) {
                    detected += TopicDlqMapping(
                        clusterId = clusterId,
                        originTopic = origin,
                        dlqTopic = candidate,
                        source = TopicDlqMapping.Source.AUTO,
                        detectedAt = now,
                        confidence = TopicDlqMapping.Confidence.HIGH,
                    )
                }
            }
        }
        mappings.rememberAutoDetected(detected)
        return detected
    }
}

@Service
class UpsertDlqMappingUseCase(private val mappings: DlqMappingPort) {
    fun execute(clusterId: String, originTopic: String, dlqTopic: String) {
        require(originTopic.isNotBlank()) { "originTopic must not be blank" }
        require(dlqTopic.isNotBlank()) { "dlqTopic must not be blank" }
        require(originTopic != dlqTopic) { "originTopic and dlqTopic must differ" }
        mappings.upsertManual(
            TopicDlqMapping(
                clusterId = clusterId,
                originTopic = originTopic,
                dlqTopic = dlqTopic,
                source = TopicDlqMapping.Source.MANUAL,
                detectedAt = Instant.now(),
            ),
        )
    }
}
