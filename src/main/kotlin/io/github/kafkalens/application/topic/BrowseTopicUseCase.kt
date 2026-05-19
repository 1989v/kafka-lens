package io.github.kafkalens.application.topic

import io.github.kafkalens.domain.ports.BrowseTopicPort
import io.github.kafkalens.domain.search.BrowsePage
import io.github.kafkalens.domain.search.BrowseQuery
import org.springframework.stereotype.Service

@Service
class BrowseTopicUseCase(private val browser: BrowseTopicPort) {
    fun execute(query: BrowseQuery): BrowsePage = browser.browse(query)
}
