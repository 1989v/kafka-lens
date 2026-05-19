package io.github.kafkalens.domain.ports

import io.github.kafkalens.domain.search.BrowsePage
import io.github.kafkalens.domain.search.BrowseQuery

interface BrowseTopicPort {
    fun browse(query: BrowseQuery): BrowsePage
}
