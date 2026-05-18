package io.github.kafkalens.application.dlq

import io.github.kafkalens.domain.dlq.DlqMessage
import io.github.kafkalens.domain.ports.DlqMappingPort
import io.github.kafkalens.domain.ports.DlqReaderPort
import org.springframework.stereotype.Service

@Service
class ReadDlqMessagesUseCase(
    private val reader: DlqReaderPort,
    private val mappings: DlqMappingPort,
) {
    fun execute(
        clusterId: String,
        dlqTopic: String,
        partition: Int?,
        fromOffset: Long?,
        limit: Int,
    ): Page {
        val mapping = mappings.getByDlq(clusterId, dlqTopic)
        val messages = reader.readDlqPage(clusterId, dlqTopic, partition, fromOffset, limit.coerceIn(1, 500))
        return Page(originTopic = mapping?.originTopic, messages = messages)
    }

    data class Page(val originTopic: String?, val messages: List<DlqMessage>)
}
