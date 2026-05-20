package io.github.kafkalens.domain.ports

interface SchemaRegistryPort {
    /**
     * Returns the raw Avro schema JSON for the given Confluent SR schema id,
     * or null when the cluster has no schemaRegistryUrl or the call fails.
     * Implementations cache by (clusterId, schemaId) — the schema bytes for a
     * given id are immutable, so the cache never needs invalidation.
     */
    fun fetchSchema(clusterId: String, schemaId: Int): String?
}
