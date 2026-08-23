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
    const val ATTR_AUDIO_PATH = "episode.audioPath"

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
        var audioUrl = base.audioUrl

        facts.groupBy { it.attribute }.values.mapNotNull { it.maxByOrNull(FactEntity::tx) }.forEach { fact ->
            when (fact.attribute) {
                ATTR_PROGRESS -> progress = fact.value.toLongOrNull() ?: progress
                ATTR_DOWNLOADED -> isDownloaded = fact.value.toBoolean()
                ATTR_IN_QUEUE -> isInQueue = fact.value.toBoolean()
                ATTR_QUEUE_ORDER -> queueOrder = fact.value.toLongOrNull() ?: queueOrder
                ATTR_LAST_PLAYED -> lastPlayed = fact.value.toLongOrNull() ?: lastPlayed
                ATTR_AUDIO_PATH -> if (fact.value.isNotBlank()) audioUrl = fact.value
            }
        }

        val hydrated = base.copy(audioUrl = audioUrl)
        hydrated.progress = progress
        hydrated.isDownloaded = isDownloaded
        hydrated.isInQueue = isInQueue
        hydrated.queueOrder = queueOrder
        hydrated.lastPlayed = lastPlayed
        return hydrated
    }
}
