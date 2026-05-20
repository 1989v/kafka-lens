package io.github.kafkalens.infrastructure.schemaregistry

import io.github.kafkalens.domain.message.ValueEncoding
import io.github.kafkalens.domain.ports.SchemaRegistryPort
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

private val log = KotlinLogging.logger {}

/**
 * Detects Confluent Avro wire format (magic byte 0x00 + 4-byte big-endian
 * schema id + payload) and decodes it to JSON via the cluster's Schema
 * Registry. Anything else is returned as a UTF-8 string. Decode failures
 * silently fall back to UTF-8 so a single bad record never breaks the page.
 */
@Component
class MessageDecoder(private val schemaRegistry: SchemaRegistryPort) {

    private val parsedSchemas = ConcurrentHashMap<String, Schema>()

    fun decodeValue(clusterId: String, valueBytes: ByteArray?): Decoded {
        if (valueBytes == null) return Decoded(null, ValueEncoding.UTF8, null)
        if (valueBytes.size < 5 || valueBytes[0] != 0x00.toByte()) {
            return Decoded(String(valueBytes, StandardCharsets.UTF_8), ValueEncoding.UTF8, null)
        }
        val schemaId = ByteBuffer.wrap(valueBytes, 1, 4).int
        val schemaJson = schemaRegistry.fetchSchema(clusterId, schemaId)
            ?: return fallback(valueBytes)

        val schema = runCatching { parsedSchemas.computeIfAbsent(schemaJson) { Schema.Parser().parse(it) } }
            .getOrElse {
                log.debug(it) { "Failed to parse schema $schemaId" }
                return fallback(valueBytes)
            }

        return runCatching {
            val reader = GenericDatumReader<Any>(schema)
            val decoder = DecoderFactory.get().binaryDecoder(valueBytes, 5, valueBytes.size - 5, null)
            val record = reader.read(null, decoder)
            val baos = ByteArrayOutputStream()
            val jsonEncoder = EncoderFactory.get().jsonEncoder(schema, baos, false)
            val writer = GenericDatumWriter<Any>(schema)
            writer.write(record, jsonEncoder)
            jsonEncoder.flush()
            Decoded(baos.toString(StandardCharsets.UTF_8), ValueEncoding.AVRO, schemaId)
        }.getOrElse {
            log.debug(it) { "Avro decode failed for schemaId=$schemaId" }
            fallback(valueBytes)
        }
    }

    private fun fallback(bytes: ByteArray) =
        Decoded(String(bytes, StandardCharsets.UTF_8), ValueEncoding.UTF8, null)

    data class Decoded(val text: String?, val encoding: ValueEncoding, val schemaId: Int?)
}
