package com.example.alakey.domain

import com.example.alakey.data.FactEntity
import com.example.alakey.data.PodcastEntity

/**
 * Information Model Logic.
 * Purifies state calculation from a stream of facts.
 */
object InformationModel {

    const val ATTR_PROGRESS = "episode.progress"
    const val ATTR_DOWNLOADED = "episode.isDownloaded"
    const val ATTR_IN_QUEUE = "episode.isInQueue"
    const val ATTR_QUEUE_ORDER = "episode.queueOrder"
    const val ATTR_LAST_PLAYED = "episode.lastPlayed"

    /**
     * Hydrates a PodcastEntity with the latest known facts.
     * This "un-complects" the core entity from its volatile state.
     */
    fun hydrate(base: PodcastEntity, facts: List<FactEntity>): PodcastEntity {
        var progress = base.progress
        var isDownloaded = base.isDownloaded
        var isInQueue = base.isInQueue
        var queueOrder = base.queueOrder
        var lastPlayed = base.lastPlayed

        facts.forEach { fact ->
            when (fact.attribute) {
                ATTR_PROGRESS -> progress = fact.value.toLongOrNull() ?: progress
                ATTR_DOWNLOADED -> isDownloaded = fact.value.toBoolean()
                ATTR_IN_QUEUE -> isInQueue = fact.value.toBoolean()
                ATTR_QUEUE_ORDER -> queueOrder = fact.value.toLongOrNull() ?: queueOrder
                ATTR_LAST_PLAYED -> lastPlayed = fact.value.toLongOrNull() ?: lastPlayed
            }
        }

        return base.copy(
            progress = progress,
            isDownloaded = isDownloaded,
            isInQueue = isInQueue,
            queueOrder = queueOrder,
            lastPlayed = lastPlayed
        )
    }
}
