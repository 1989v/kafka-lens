package io.github.kafkalens.presentation.rest

import io.github.kafkalens.application.stats.GetTopicStatsUseCase
import io.github.kafkalens.application.stats.TopicStats
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/clusters/{clusterId}")
class TopicStatsController(private val stats: GetTopicStatsUseCase) {

    @GetMapping("/topics/{topic}/stats")
    fun stats(@PathVariable clusterId: String, @PathVariable topic: String): TopicStats =
        stats.execute(clusterId, topic)
}
