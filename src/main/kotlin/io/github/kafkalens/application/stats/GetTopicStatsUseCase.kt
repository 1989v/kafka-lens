package io.github.kafkalens.application.stats

import io.github.kafkalens.domain.ports.KafkaAdminPort
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/**
 * Combines the topic's structural info (partitions / end offsets), the
 * current per-group lag snapshot, and the rolling time series from
 * [TopicStatsCollector] into a single payload the Stats tab can render
 * without follow-up calls.
 *
 * Rates and ETAs are derived from the first and last samples in the
 * available window; if there's only one sample, the rate fields are null
 * (the FE renders them as "—").
 */
@Service
class GetTopicStatsUseCase(
    private val admin: KafkaAdminPort,
    private val collector: TopicStatsCollector,
) {
    fun execute(clusterId: String, topic: String): TopicStats {
        val meta = admin.getTopic(clusterId, topic)
            ?: throw IllegalArgumentException("Topic not found: $topic")
        val now = Instant.now()

        val series = collector.samplesFor(clusterId, topic)
        val first = series.firstOrNull()
        val last = series.lastOrNull()
        val windowSeconds: Long? =
            if (first != null && last != null && first !== last)
                Duration.between(first.timestamp, last.timestamp).toSeconds().takeIf { it > 0 }
            else null

        val currentEndOffset = meta.partitions.sumOf { it.endOffset }
        val currentBeginningOffset = meta.partitions.sumOf { it.beginningOffset }
        val currentLagByGroup = last?.lagByGroup ?: emptyMap()
        val totalLag = currentLagByGroup.values.sum()

        val productionRatePerSec: Double? =
            if (windowSeconds != null) ((last!!.endOffset - first!!.endOffset).toDouble() / windowSeconds)
                .takeIf { it.isFinite() && it >= 0.0 }
            else null

        val groupMetrics: List<GroupMetric> = currentLagByGroup.keys
            .sortedByDescending { currentLagByGroup[it] ?: 0L }
            .map { group ->
                val currentLag = currentLagByGroup[group] ?: 0L
                val rate: Double? =
                    if (windowSeconds != null && first != null && last != null) {
                        val firstLag = first.lagByGroup[group] ?: currentLag
                        // consumeRate = production - lagChange. positive when group catching up.
                        val produced = (last.endOffset - first.endOffset).toDouble()
                        val lagDelta = (currentLag - firstLag).toDouble()
                        ((produced - lagDelta) / windowSeconds).takeIf { it.isFinite() && it >= 0.0 }
                    } else null
                val drainSeconds: Long? =
                    if (rate != null && rate > 0.0 && currentLag > 0L) (currentLag / rate).toLong() else null
                GroupMetric(
                    groupId = group,
                    currentLag = currentLag,
                    consumeRatePerSec = rate,
                    drainEtaSeconds = drainSeconds,
                )
            }

        val partitionDistribution = meta.partitions.map {
            PartitionPoint(
                partition = it.partition,
                beginningOffset = it.beginningOffset,
                endOffset = it.endOffset,
                messages = (it.endOffset - it.beginningOffset).coerceAtLeast(0),
            )
        }

        val seriesDtos = series.map { s ->
            SamplePoint(
                timestamp = s.timestamp,
                endOffset = s.endOffset,
                lagByGroup = s.lagByGroup,
            )
        }

        return TopicStats(
            clusterId = clusterId,
            topic = topic,
            sampledAt = now,
            partitions = meta.partitions.size,
            currentEndOffset = currentEndOffset,
            currentBeginningOffset = currentBeginningOffset,
            availableMessages = (currentEndOffset - currentBeginningOffset).coerceAtLeast(0),
            totalLag = totalLag,
            productionRatePerSec = productionRatePerSec,
            windowSeconds = windowSeconds,
            samplesAvailable = series.size,
            groups = groupMetrics,
            partitionDistribution = partitionDistribution,
            series = seriesDtos,
        )
    }
}

data class TopicStats(
    val clusterId: String,
    val topic: String,
    val sampledAt: Instant,
    val partitions: Int,
    val currentEndOffset: Long,
    val currentBeginningOffset: Long,
    val availableMessages: Long,
    val totalLag: Long,
    val productionRatePerSec: Double?,
    val windowSeconds: Long?,
    val samplesAvailable: Int,
    val groups: List<GroupMetric>,
    val partitionDistribution: List<PartitionPoint>,
    val series: List<SamplePoint>,
)

data class GroupMetric(
    val groupId: String,
    val currentLag: Long,
    val consumeRatePerSec: Double?,
    val drainEtaSeconds: Long?,
)

data class PartitionPoint(
    val partition: Int,
    val beginningOffset: Long,
    val endOffset: Long,
    val messages: Long,
)

data class SamplePoint(
    val timestamp: Instant,
    val endOffset: Long,
    val lagByGroup: Map<String, Long>,
)
