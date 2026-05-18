package io.github.kafkalens.infrastructure.kafka

import io.github.kafkalens.domain.ports.DlqMappingPort
import io.github.kafkalens.domain.ports.MessagePublisherPort
import io.github.kafkalens.domain.ports.PublishResult
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component

@Component
class KafkaPublisherAdapter(
    private val factory: KafkaClientFactory,
    private val dlqMappings: DlqMappingPort,
) : MessagePublisherPort {

    override fun publish(
        clusterId: String,
        topic: String,
        key: String?,
        value: String,
        headers: Map<String, String>,
    ): PublishResult {
        if (dlqMappings.getByDlq(clusterId, topic) != null) {
            throw DirectDlqPublishForbidden(topic)
        }
        val producer = factory.producer(clusterId)
        val record = ProducerRecord<ByteArray, ByteArray>(
            topic,
            null,
            key?.toByteArray(),
            value.toByteArray(),
        )
        headers.forEach { (k, v) -> record.headers().add(k, v.toByteArray()) }
        val md = producer.send(record).get()
        return PublishResult(md.partition(), md.offset(), md.timestamp())
    }
}

class DirectDlqPublishForbidden(val topic: String) :
    RuntimeException("Direct publishing to DLQ topic '$topic' is forbidden by Kafka Lens policy. Use the reprocess flow instead.")
