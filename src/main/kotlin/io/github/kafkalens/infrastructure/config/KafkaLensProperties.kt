package io.github.kafkalens.infrastructure.config

import io.github.kafkalens.domain.cluster.ClusterConfig
import io.github.kafkalens.domain.cluster.SecurityConfig
import io.github.kafkalens.domain.search.ScanLimits
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "")
data class KafkaLensProperties(
    val clusters: List<ClusterProps> = emptyList(),
    val storage: StorageProps = StorageProps(),
    val scan: ScanProps = ScanProps(),
    val dlq: DlqProps = DlqProps(),
    val auth: AuthProps = AuthProps(),
) {
    data class ClusterProps(
        var id: String = "",
        var name: String = "",
        var bootstrapServers: String = "",
        var security: SecurityProps = SecurityProps(),
        var dlqNamingPatterns: List<String> = emptyList(),
        var clientProperties: Map<String, String> = emptyMap(),
    ) {
        fun toDomain(): ClusterConfig {
            val patterns = dlqNamingPatterns.ifEmpty { ClusterConfig.DEFAULT_DLQ_PATTERNS }
            return ClusterConfig(
                id = id,
                name = name.ifBlank { id },
                bootstrapServers = bootstrapServers,
                security = security.toDomain(),
                dlqNamingPatterns = patterns,
                clientProperties = clientProperties,
            )
        }
    }

    data class SecurityProps(
        var protocol: String = "PLAINTEXT",
        var saslMechanism: String? = null,
        var saslJaasConfig: String? = null,
        var sslTruststoreLocation: String? = null,
        var sslTruststorePassword: String? = null,
        var sslKeystoreLocation: String? = null,
        var sslKeystorePassword: String? = null,
    ) {
        fun toDomain() = SecurityConfig(
            protocol = protocol,
            saslMechanism = saslMechanism,
            saslJaasConfig = saslJaasConfig,
            sslTruststoreLocation = sslTruststoreLocation,
            sslTruststorePassword = sslTruststorePassword,
            sslKeystoreLocation = sslKeystoreLocation,
            sslKeystorePassword = sslKeystorePassword,
        )
    }

    data class StorageProps(var sqlitePath: String = "./data/kafka-lens.db")

    data class ScanProps(
        var maxMessagesPerJob: Long = 100_000,
        var maxScanDurationSeconds: Long = 120,
        var defaultPageSize: Int = 100,
    ) {
        fun toDomain() = ScanLimits(maxMessagesPerJob, maxScanDurationSeconds, defaultPageSize)
    }

    data class DlqProps(var reprocess: ReprocessProps = ReprocessProps())
    data class ReprocessProps(
        var requireConfirmation: Boolean = true,
        var maxBatchSize: Int = 1000,
        var duplicateDetectionWindow: String = "24h",
    )

    data class AuthProps(
        var mode: String = "none",
        var basic: BasicAuthProps = BasicAuthProps(),
        var oidc: OidcAuthProps = OidcAuthProps(),
    )

    data class BasicAuthProps(var username: String = "", var passwordHash: String = "")

    data class OidcAuthProps(
        var issuerUri: String = "",
        var clientId: String = "",
        var clientSecret: String = "",
        var adminClaim: String = "",
        var adminValues: List<String> = emptyList(),
    )
}
