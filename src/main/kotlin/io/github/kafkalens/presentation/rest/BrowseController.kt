package io.github.kafkalens.presentation.rest

import io.github.kafkalens.application.topic.BrowseTopicUseCase
import io.github.kafkalens.domain.search.BrowseMode
import io.github.kafkalens.domain.search.BrowsePage
import io.github.kafkalens.domain.search.BrowseQuery
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/clusters/{clusterId}")
class BrowseController(private val browse: BrowseTopicUseCase) {

    @PostMapping("/topics/{topic}/browse")
    fun browseTopic(
        @PathVariable clusterId: String,
        @PathVariable topic: String,
        @RequestBody req: BrowseRequest,
    ): BrowsePage = browse.execute(
        BrowseQuery(
            clusterId = clusterId,
            topic = topic,
            mode = req.mode,
            partitions = req.partitions,
            pageSize = req.pageSize ?: 50,
            fromOffset = req.fromOffset,
            fromTimestamp = req.fromTimestamp,
            toTimestamp = req.toTimestamp,
            keyContains = req.keyContains,
            valueContains = req.valueContains,
            timeoutSeconds = req.timeoutSeconds ?: 30,
        ),
    )

    data class BrowseRequest(
        val mode: BrowseMode = BrowseMode.LATEST,
        val partitions: List<Int>? = null,
        val pageSize: Int? = null,
        val fromOffset: Map<Int, Long>? = null,
        val fromTimestamp: Instant? = null,
        val toTimestamp: Instant? = null,
        val keyContains: String? = null,
        val valueContains: String? = null,
        val timeoutSeconds: Long? = null,
    )
}
