package io.github.kafkalens.presentation.rest

import io.github.kafkalens.domain.ports.BrokerInfo
import io.github.kafkalens.domain.ports.KafkaAdminPort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/clusters/{clusterId}")
class BrokersController(private val admin: KafkaAdminPort) {

    @GetMapping("/brokers")
    fun list(@PathVariable clusterId: String): List<BrokerInfo> = admin.listBrokers(clusterId)
}
