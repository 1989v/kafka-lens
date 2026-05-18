package io.github.kafkalens.domain.ports

import io.github.kafkalens.domain.cluster.ClusterConfig

interface ClusterRegistry {
    fun list(): List<ClusterConfig>
    fun get(id: String): ClusterConfig?
    fun require(id: String): ClusterConfig =
        get(id) ?: throw IllegalArgumentException("Cluster not found: $id")
}
