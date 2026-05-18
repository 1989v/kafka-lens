package io.github.kafkalens.domain.cluster

data class ClusterConfig(
    val id: String,
    val name: String,
    val bootstrapServers: String,
    val security: SecurityConfig = SecurityConfig(),
    val dlqNamingPatterns: List<String> = DEFAULT_DLQ_PATTERNS,
    val clientProperties: Map<String, String> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "cluster id must not be blank" }
        require(bootstrapServers.isNotBlank()) { "bootstrapServers must not be blank" }
    }

    companion object {
        val DEFAULT_DLQ_PATTERNS = listOf("{topic}.DLT", "{topic}-dlq", "dead-letter-{topic}")
    }
}

data class SecurityConfig(
    val protocol: String = "PLAINTEXT",
    val saslMechanism: String? = null,
    val saslJaasConfig: String? = null,
    val sslTruststoreLocation: String? = null,
    val sslTruststorePassword: String? = null,
    val sslKeystoreLocation: String? = null,
    val sslKeystorePassword: String? = null,
)
