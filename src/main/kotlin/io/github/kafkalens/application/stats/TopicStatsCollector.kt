package io.github.kafkalens.application.stats

import io.github.kafkalens.domain.ports.ClusterRegistry
import io.github.kafkalens.domain.ports.KafkaAdminPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

/**
 * Background sampler that snapshots every cluster's topic-level metrics into
 * an in-memory ring buffer. The buffer holds the last [MAX_SAMPLES] entries
 * per (cluster, topic) so the stats endpoint can hand the FE a recent time
 * series without depending on Prometheus/InfluxDB.
 *
 * Sampling is best-effort: a failure for one cluster doesn't block the rest,
 * and a slow cluster simply skips that tick.
 */
@Component
class TopicStatsCollector(
    private val registry: ClusterRegistry,
    private val admin: KafkaAdminPort,
) {
    private val log = KotlinLogging.logger {}

    // cluster id → topic name → samples (oldest → newest)
    private val buffers = ConcurrentHashMap<String, ConcurrentHashMap<String, ArrayDeque<TopicSample>>>()

    @Scheduled(fixedDelayString = "\${stats.sample-interval-ms:10000}", initialDelay = 5_000)
    fun sample() {
        for (cluster in registry.list()) {
            runCatching { collectCluster(cluster.id) }
                .onFailure { log.warn(it) { "Stats sampling failed for cluster ${cluster.id}" } }
        }
    }

    private fun collectCluster(clusterId: String) {
        val topics = admin.listTopics(clusterId, includeInternal = false)
        if (topics.isEmpty()) return

        val groups = runCatching { admin.listConsumerGroups(clusterId) }.getOrDefault(emptyList())
        val now = Instant.now()

        val lagByTopicGroup = HashMap<String, MutableMap<String, Long>>(topics.size)
        for (g in groups) {
            for (o in g.offsets) {
                lagByTopicGroup
                    .getOrPut(o.topic) { mutableMapOf() }
                    .merge(g.groupId, o.lag) { a, b -> a + b }
            }
        }

        val clusterBuf = buffers.getOrPut(clusterId) { ConcurrentHashMap() }
        for (topic in topics) {
            val endOffset = topic.partitions.sumOf { it.endOffset }
            val sample = TopicSample(
                timestamp = now,
                endOffset = endOffset,
                lagByGroup = lagByTopicGroup[topic.name] ?: emptyMap(),
            )
            val buf = clusterBuf.getOrPut(topic.name) { ArrayDeque(MAX_SAMPLES) }
            synchronized(buf) {
                buf.addLast(sample)
                while (buf.size > MAX_SAMPLES) buf.removeFirst()
            }
        }
    }

    fun samplesFor(clusterId: String, topic: String): List<TopicSample> {
        val buf = buffers[clusterId]?.get(topic) ?: return emptyList()
        return synchronized(buf) { buf.toList() }
    }

    companion object {
        // 60 samples at 10s interval = 10 minutes of history per topic.
        const val MAX_SAMPLES = 60
    }
}
