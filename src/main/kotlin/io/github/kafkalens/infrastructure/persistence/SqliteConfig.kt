package io.github.kafkalens.infrastructure.persistence

import io.github.kafkalens.infrastructure.config.KafkaLensProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import java.nio.file.Files
import java.nio.file.Path
import javax.sql.DataSource

private val log = KotlinLogging.logger {}

@Configuration
class SqliteConfig(private val props: KafkaLensProperties) {

    @Bean
    fun dataSource(): DataSource {
        val raw = props.storage.sqlitePath
        val path = Path.of(raw).toAbsolutePath()
        path.parent?.let { Files.createDirectories(it) }
        log.info { "SQLite data store: $path" }
        return DriverManagerDataSource("jdbc:sqlite:$path").also {
            it.setDriverClassName("org.sqlite.JDBC")
        }
    }

    @Bean
    fun jdbcTemplate(dataSource: DataSource): JdbcTemplate = JdbcTemplate(dataSource)
}

@Configuration
class SchemaInitializer(private val jdbc: JdbcTemplate) {

    @PostConstruct
    fun migrate() {
        jdbc.execute(
            """
            CREATE TABLE IF NOT EXISTS topic_dlq_mapping (
              cluster_id TEXT NOT NULL,
              origin_topic TEXT NOT NULL,
              dlq_topic TEXT NOT NULL,
              source TEXT NOT NULL,
              confidence TEXT NOT NULL DEFAULT 'HIGH',
              detected_at TEXT NOT NULL,
              PRIMARY KEY (cluster_id, origin_topic, dlq_topic)
            )
            """.trimIndent(),
        )
        jdbc.execute(
            """
            CREATE TABLE IF NOT EXISTS reprocess_job (
              id TEXT PRIMARY KEY,
              cluster_id TEXT NOT NULL,
              dlq_topic TEXT NOT NULL,
              origin_topic TEXT NOT NULL,
              mode TEXT NOT NULL,
              status TEXT NOT NULL,
              requested_by TEXT NOT NULL,
              created_at TEXT NOT NULL,
              completed_at TEXT,
              total_requested INTEGER NOT NULL,
              succeeded INTEGER NOT NULL,
              failed INTEGER NOT NULL,
              notes TEXT
            )
            """.trimIndent(),
        )
        jdbc.execute(
            """
            CREATE TABLE IF NOT EXISTS reprocess_dedupe (
              fingerprint TEXT PRIMARY KEY,
              expires_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        jdbc.execute(
            """
            CREATE TABLE IF NOT EXISTS publish_history (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              cluster_id TEXT NOT NULL,
              topic TEXT NOT NULL,
              actor TEXT NOT NULL,
              key TEXT,
              value TEXT NOT NULL,
              headers TEXT,
              created_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        jdbc.execute(
            """
            CREATE TABLE IF NOT EXISTS saved_search (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              cluster_id TEXT NOT NULL,
              name TEXT NOT NULL,
              query_json TEXT NOT NULL,
              created_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
    }
}
