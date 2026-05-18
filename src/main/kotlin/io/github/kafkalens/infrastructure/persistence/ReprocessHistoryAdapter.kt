package io.github.kafkalens.infrastructure.persistence

import io.github.kafkalens.domain.dlq.ReprocessJob
import io.github.kafkalens.domain.ports.ReprocessHistoryPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.Instant

@Component
class ReprocessHistoryAdapter(private val jdbc: JdbcTemplate) : ReprocessHistoryPort {

    private val rowMapper = RowMapper { rs: ResultSet, _: Int ->
        ReprocessJob(
            id = rs.getString("id"),
            clusterId = rs.getString("cluster_id"),
            dlqTopic = rs.getString("dlq_topic"),
            originTopic = rs.getString("origin_topic"),
            mode = ReprocessJob.Mode.valueOf(rs.getString("mode")),
            status = ReprocessJob.Status.valueOf(rs.getString("status")),
            requestedBy = rs.getString("requested_by"),
            createdAt = Instant.parse(rs.getString("created_at")),
            completedAt = rs.getString("completed_at")?.let(Instant::parse),
            totalRequested = rs.getInt("total_requested"),
            succeeded = rs.getInt("succeeded"),
            failed = rs.getInt("failed"),
            notes = rs.getString("notes"),
        )
    }

    override fun save(job: ReprocessJob) {
        jdbc.update(
            """
            INSERT INTO reprocess_job (id, cluster_id, dlq_topic, origin_topic, mode, status, requested_by,
                                       created_at, completed_at, total_requested, succeeded, failed, notes)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
              status = excluded.status,
              completed_at = excluded.completed_at,
              succeeded = excluded.succeeded,
              failed = excluded.failed,
              notes = excluded.notes
            """.trimIndent(),
            job.id, job.clusterId, job.dlqTopic, job.originTopic,
            job.mode.name, job.status.name, job.requestedBy,
            job.createdAt.toString(), job.completedAt?.toString(),
            job.totalRequested, job.succeeded, job.failed, job.notes,
        )
    }

    override fun list(clusterId: String, limit: Int): List<ReprocessJob> =
        jdbc.query(
            "SELECT * FROM reprocess_job WHERE cluster_id = ? ORDER BY created_at DESC LIMIT ?",
            rowMapper, clusterId, limit,
        )

    override fun get(id: String): ReprocessJob? =
        jdbc.query("SELECT * FROM reprocess_job WHERE id = ?", rowMapper, id).firstOrNull()
}
