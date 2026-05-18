package io.github.kafkalens.presentation.rest

import io.github.kafkalens.application.dlq.AutoDetectDlqMappingsUseCase
import io.github.kafkalens.application.dlq.ListDlqMappingsUseCase
import io.github.kafkalens.application.dlq.ReadDlqMessagesUseCase
import io.github.kafkalens.application.dlq.ReprocessDlqUseCase
import io.github.kafkalens.application.dlq.UpsertDlqMappingUseCase
import io.github.kafkalens.domain.dlq.ReprocessJob
import io.github.kafkalens.domain.dlq.TopicDlqMapping
import io.github.kafkalens.domain.ports.DlqMappingPort
import io.github.kafkalens.domain.ports.ReprocessHistoryPort
import io.github.kafkalens.presentation.ActorResolver
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/clusters/{clusterId}/dlq")
class DlqController(
    private val listMappings: ListDlqMappingsUseCase,
    private val autoDetect: AutoDetectDlqMappingsUseCase,
    private val upsertMapping: UpsertDlqMappingUseCase,
    private val mappings: DlqMappingPort,
    private val read: ReadDlqMessagesUseCase,
    private val reprocess: ReprocessDlqUseCase,
    private val history: ReprocessHistoryPort,
    private val actorResolver: ActorResolver,
) {
    @GetMapping("/mappings")
    fun listMappings(
        @PathVariable clusterId: String,
        @RequestParam(defaultValue = "false") refresh: Boolean,
    ): List<TopicDlqMapping> = listMappings.execute(clusterId, refresh)

    @PostMapping("/mappings/auto-detect")
    fun runAutoDetect(@PathVariable clusterId: String): List<TopicDlqMapping> =
        autoDetect.execute(clusterId)

    @PutMapping("/mappings")
    fun upsertMapping(@PathVariable clusterId: String, @RequestBody req: MappingRequest) {
        upsertMapping.execute(clusterId, req.originTopic, req.dlqTopic)
    }

    @DeleteMapping("/mappings")
    fun deleteMapping(
        @PathVariable clusterId: String,
        @RequestParam originTopic: String,
        @RequestParam dlqTopic: String,
    ) {
        mappings.deleteManual(clusterId, originTopic, dlqTopic)
    }

    @GetMapping("/topics/{dlqTopic}/messages")
    fun readMessages(
        @PathVariable clusterId: String,
        @PathVariable dlqTopic: String,
        @RequestParam(required = false) partition: Int?,
        @RequestParam(required = false) fromOffset: Long?,
        @RequestParam(defaultValue = "100") limit: Int,
    ): ReadDlqMessagesUseCase.Page = read.execute(clusterId, dlqTopic, partition, fromOffset, limit)

    @PostMapping("/topics/{dlqTopic}/reprocess")
    fun reprocess(
        @PathVariable clusterId: String,
        @PathVariable dlqTopic: String,
        @RequestBody req: ReprocessRequest,
        request: HttpServletRequest,
    ): ReprocessJob = reprocess.execute(
        clusterId = clusterId,
        dlqTopic = dlqTopic,
        targets = req.targets.map { ReprocessDlqUseCase.Target(it.partition, it.offset) },
        actor = actorResolver.resolve(request),
        mode = req.mode,
        notes = req.notes,
    )

    @GetMapping("/reprocess-history")
    fun reprocessHistory(
        @PathVariable clusterId: String,
        @RequestParam(defaultValue = "50") limit: Int,
    ): List<ReprocessJob> = history.list(clusterId, limit.coerceIn(1, 500))

    data class MappingRequest(val originTopic: String, val dlqTopic: String)
    data class ReprocessRequest(
        val targets: List<TargetDto>,
        val mode: ReprocessJob.Mode = ReprocessJob.Mode.GROUP,
        val notes: String? = null,
    )
    data class TargetDto(val partition: Int, val offset: Long)
}
