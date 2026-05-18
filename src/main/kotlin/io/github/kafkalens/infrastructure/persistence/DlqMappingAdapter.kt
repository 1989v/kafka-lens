package io.github.kafkalens.infrastructure.persistence

import io.github.kafkalens.domain.dlq.TopicDlqMapping
import io.github.kafkalens.domain.ports.DlqMappingPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Instant

@Component
class DlqMappingAdapter(private val jdbc: JdbcTemplate) : DlqMappingPort {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        TopicDlqMapping(
            clusterId = rs.getString("cluster_id"),
            originTopic = rs.getString("origin_topic"),
            dlqTopic = rs.getString("dlq_topic"),
            source = TopicDlqMapping.Source.valueOf(rs.getString("source")),
            confidence = TopicDlqMapping.Confidence.valueOf(rs.getString("confidence")),
            detectedAt = Instant.parse(rs.getString("detected_at")),
        )
    }

    override fun list(clusterId: String): List<TopicDlqMapping> =
        jdbc.query("SELECT * FROM topic_dlq_mapping WHERE cluster_id = ? ORDER BY dlq_topic", rowMapper, clusterId)

    override fun getByDlq(clusterId: String, dlqTopic: String): TopicDlqMapping? =
        jdbc.query(
            "SELECT * FROM topic_dlq_mapping WHERE cluster_id = ? AND dlq_topic = ? ORDER BY source DESC LIMIT 1",
            rowMapper, clusterId, dlqTopic,
        ).firstOrNull()

    override fun getByOrigin(clusterId: String, originTopic: String): List<TopicDlqMapping> =
        jdbc.query(
            "SELECT * FROM topic_dlq_mapping WHERE cluster_id = ? AND origin_topic = ?",
            rowMapper, clusterId, originTopic,
        )

    override fun upsertManual(mapping: TopicDlqMapping) {
        jdbc.update(
            """
            INSERT INTO topic_dlq_mapping (cluster_id, origin_topic, dlq_topic, source, confidence, detected_at)
            VALUES (?, ?, ?, 'MANUAL', ?, ?)
            ON CONFLICT(cluster_id, origin_topic, dlq_topic) DO UPDATE SET
              source = 'MANUAL',
              confidence = excluded.confidence,
              detected_at = excluded.detected_at
            """.trimIndent(),
            mapping.clusterId, mapping.originTopic, mapping.dlqTopic,
            mapping.confidence.name, mapping.detectedAt.toString(),
        )
    }

    override fun deleteManual(clusterId: String, originTopic: String, dlqTopic: String) {
        jdbc.update(
            "DELETE FROM topic_dlq_mapping WHERE cluster_id = ? AND origin_topic = ? AND dlq_topic = ? AND source = 'MANUAL'",
            clusterId, originTopic, dlqTopic,
        )
    }

    override fun rememberAutoDetected(mappings: List<TopicDlqMapping>) {
        if (mappings.isEmpty()) return
        jdbc.batchUpdate(
            """
            INSERT INTO topic_dlq_mapping (cluster_id, origin_topic, dlq_topic, source, confidence, detected_at)
            VALUES (?, ?, ?, 'AUTO', ?, ?)
            ON CONFLICT(cluster_id, origin_topic, dlq_topic) DO UPDATE SET
              confidence = excluded.confidence,
              detected_at = excluded.detected_at
            WHERE topic_dlq_mapping.source = 'AUTO'
            """.trimIndent(),
            mappings.map {
                arrayOf<Any>(it.clusterId, it.originTopic, it.dlqTopic, it.confidence.name, it.detectedAt.toString())
            },
        )
    }
}
