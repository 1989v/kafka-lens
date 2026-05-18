package io.github.kafkalens.application.publish

import io.github.kafkalens.domain.ports.MessagePublisherPort
import io.github.kafkalens.domain.ports.PublishResult
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PublishMessageUseCase(
    private val publisher: MessagePublisherPort,
    private val jdbc: JdbcTemplate,
) {
    fun execute(
        clusterId: String,
        topic: String,
        key: String?,
        value: String,
        headers: Map<String, String>,
        actor: String,
    ): PublishResult {
        val result = publisher.publish(clusterId, topic, key, value, headers)
        jdbc.update(
            """
            INSERT INTO publish_history (cluster_id, topic, actor, key, value, headers, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            clusterId, topic, actor, key, value,
            if (headers.isEmpty()) null else headers.entries.joinToString(";") { "${it.key}=${it.value}" },
            Instant.now().toString(),
        )
        return result
    }
}
