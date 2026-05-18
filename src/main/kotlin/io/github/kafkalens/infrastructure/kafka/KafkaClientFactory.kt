package io.github.kafkalens.infrastructure.kafka

import io.github.kafkalens.domain.cluster.ClusterConfig
import io.github.kafkalens.domain.ports.ClusterRegistry
import jakarta.annotation.PreDestroy
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.springframework.stereotype.Component
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

/**
 * Lazily caches one [AdminClient] and one [KafkaProducer] per cluster so we don't
 * pay the connection-setup cost on every REST call. Consumers are NOT cached —
 * each search opens a transient consumer in a unique group and closes it after.
 */
@Component
class KafkaClientFactory(private val registry: ClusterRegistry) {
    private val admins = ConcurrentHashMap<String, AdminClient>()
    private val producers = ConcurrentHashMap<String, KafkaProducer<ByteArray, ByteArray>>()

    fun admin(clusterId: String): AdminClient =
        admins.computeIfAbsent(clusterId) { id ->
            AdminClient.create(adminProps(registry.require(id)))
        }

    fun producer(clusterId: String): KafkaProducer<ByteArray, ByteArray> =
        producers.computeIfAbsent(clusterId) { id ->
            KafkaProducer(producerProps(registry.require(id)))
        }

    fun newConsumer(clusterId: String, groupId: String, autoOffsetReset: String = "earliest"): KafkaConsumer<ByteArray, ByteArray> {
        val cfg = registry.require(clusterId)
        val props = baseProps(cfg).apply {
            put(ConsumerConfig.GROUP_ID_CONFIG, groupId)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset)
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java.name)
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500)
            put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500)
            put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000)
        }
        return KafkaConsumer(props)
    }

    @PreDestroy
    fun shutdown() {
        admins.values.forEach { runCatching { it.close() } }
        producers.values.forEach { runCatching { it.close() } }
        admins.clear()
        producers.clear()
    }

    private fun adminProps(cfg: ClusterConfig): Properties = baseProps(cfg).apply {
        put(AdminClientConfig.CLIENT_ID_CONFIG, "kafka-lens-admin-${cfg.id}")
        put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000)
    }

    private fun producerProps(cfg: ClusterConfig): Properties = baseProps(cfg).apply {
        put(ProducerConfig.CLIENT_ID_CONFIG, "kafka-lens-producer-${cfg.id}")
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
    }

    private fun baseProps(cfg: ClusterConfig): Properties {
        val props = Properties()
        props[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = cfg.bootstrapServers
        cfg.security.protocol.takeIf { it.isNotBlank() }?.let { props[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = it }
        cfg.security.saslMechanism?.let { props[SaslConfigs.SASL_MECHANISM] = it }
        cfg.security.saslJaasConfig?.let { props[SaslConfigs.SASL_JAAS_CONFIG] = it }
        cfg.security.sslTruststoreLocation?.let { props[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] = it }
        cfg.security.sslTruststorePassword?.let { props[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] = it }
        cfg.security.sslKeystoreLocation?.let { props[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] = it }
        cfg.security.sslKeystorePassword?.let { props[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] = it }
        cfg.clientProperties.forEach { (k, v) -> props[k] = v }
        return props
    }
}
