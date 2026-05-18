package io.github.kafkalens.infrastructure.persistence

import io.github.kafkalens.domain.ports.DuplicateDetectionPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class DuplicateDetectionAdapter(private val jdbc: JdbcTemplate) : DuplicateDetectionPort {

    override fun wasRecentlyReprocessed(fingerprint: String): Boolean {
        purgeExpired()
        val count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM reprocess_dedupe WHERE fingerprint = ?",
            Int::class.java,
            fingerprint,
        ) ?: 0
        return count > 0
    }

    override fun markReprocessed(fingerprint: String, ttl: Duration) {
        val expiresAt = Instant.now().plus(ttl).toEpochMilli()
        jdbc.update(
            """
            INSERT INTO reprocess_dedupe (fingerprint, expires_at) VALUES (?, ?)
            ON CONFLICT(fingerprint) DO UPDATE SET expires_at = excluded.expires_at
            """.trimIndent(),
            fingerprint, expiresAt,
        )
    }

    private fun purgeExpired() {
        jdbc.update("DELETE FROM reprocess_dedupe WHERE expires_at < ?", Instant.now().toEpochMilli())
    }
}
