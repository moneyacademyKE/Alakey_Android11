package com.example.alakey.ui

import com.example.alakey.data.PodcastEntity
import com.example.alakey.domain.PureLogic

/** Deterministic library views; the UI only renders the result. */
object LibraryFilters {
    fun apply(filter: String, podcasts: List<PodcastEntity>): List<PodcastEntity> = when (filter) {
        "Continue" -> podcasts
            .filter { it.progress > 0 && !isFinished(it) }
            .sortedByDescending { it.lastPlayed }
        "New" -> podcasts
            .filter { it.progress == 0L }
            .sortedByDescending { PureLogic.parseDate(it.pubDate) }
        "Short" -> podcasts
            .filter { !isFinished(it) && it.duration in 1..1200 }
            .sortedByDescending { PureLogic.parseDate(it.pubDate) }
        else -> podcasts.sortedByDescending { PureLogic.parseDate(it.pubDate) }
    }

    private fun isFinished(podcast: PodcastEntity): Boolean =
        podcast.duration > 0 && podcast.progress >= podcast.duration * 0.95
}
