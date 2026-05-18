package io.github.kafkalens.domain.cluster

/**
 * Snapshot of broker-side capabilities derived from the ApiVersions response and
 * DescribeCluster metadata. Used by the UI to disable features the broker can't serve.
 *
 * Why: connecting to older Kafka brokers (2.x) is supported, but some calls
 * (e.g. incremental config alters, KRaft-only APIs) need newer versions. We
 * surface the cap in the response so the UI can show "broker N.M+ required"
 * instead of failing opaquely.
 */
data class BrokerFeatureMatrix(
    val clusterId: String,
    val brokerVersion: String?,
    val brokerCount: Int,
    val kraftMode: Boolean?,
    val supports: Map<Feature, Boolean>,
) {
    fun has(feature: Feature): Boolean = supports[feature] == true

    enum class Feature {
        INCREMENTAL_ALTER_CONFIGS,
        DESCRIBE_PRODUCERS,
        LIST_OFFSETS_BY_TIMESTAMP,
        CONSUMER_GROUP_DESCRIBE_V2,
        ALTER_CONSUMER_GROUP_OFFSETS,
    }
}
