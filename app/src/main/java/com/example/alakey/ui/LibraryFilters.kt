package com.example.alakey.ui

import com.example.alakey.data.PodcastEntity
import com.example.alakey.domain.PureLogic

/** Deterministic library views; the UI only renders the result. */
object LibraryFilters {
    /** M54: duration is MILLISECONDS — 1..1200 matched nothing (≤1.2s, 0 excluded). Intent was ~20 minutes. */
    private const val SHORT_MAX_MS = 20 * 60 * 1000L

    fun apply(filter: String, podcasts: List<PodcastEntity>): List<PodcastEntity> = when (filter) {
        "Continue" -> podcasts
            .filter { it.progress > 0 && !isFinished(it) }
            .sortedByDescending { it.lastPlayed }
        "New" -> podcasts
            .filter { it.progress == 0L }
            .sortedByDescending { PureLogic.parseDateOrNull(it.pubDate) }
        "Short" -> podcasts
            .filter { !isFinished(it) && it.duration in 1..SHORT_MAX_MS }
            .sortedByDescending { PureLogic.parseDateOrNull(it.pubDate) }
        else -> podcasts.sortedByDescending { PureLogic.parseDateOrNull(it.pubDate) }
    }

    private fun isFinished(podcast: PodcastEntity): Boolean =
        podcast.duration > 0 && podcast.progress >= podcast.duration * 0.95
}
