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
     * L73: the readable fact vocabulary. The write side (FactStore.assert)
     * rejects keys outside this set — previously arbitrary strings were
     * storable forever and loaded into heap on every hydration while the
     * reader's `when` dropped them silently.
     */
    val KNOWN_ATTRIBUTES: Set<String> = setOf(
        ATTR_PROGRESS, ATTR_DOWNLOADED, ATTR_IN_QUEUE,
        ATTR_QUEUE_ORDER, ATTR_LAST_PLAYED, ATTR_AUDIO_PATH
    )

    /** L73: strict boolean parse — toBoolean() maps ANY garbage to false. */
    fun parseStrictBoolean(value: String, previous: Boolean): Boolean = when (value.trim()) {
        "true" -> true
        "false" -> false
        else -> previous
    }

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
                // L73: strict parse — garbage keeps the previous flag instead of
                // silently clearing it (toBoolean() mapped any junk to false).
                ATTR_DOWNLOADED -> isDownloaded = parseStrictBoolean(fact.value, isDownloaded)
                ATTR_IN_QUEUE -> isInQueue = parseStrictBoolean(fact.value, isInQueue)
                ATTR_QUEUE_ORDER -> queueOrder = fact.value.toLongOrNull() ?: queueOrder
                ATTR_LAST_PLAYED -> lastPlayed = fact.value.toLongOrNull() ?: lastPlayed
                ATTR_AUDIO_PATH -> if (fact.value.isNotBlank()) audioUrl = fact.value
                // L73: unknown keys are dropped by design, but now they are
                // also un-writable (FactStore.assert rejects them) — this arm
                // exists only for rows written before that gate.
                else -> Unit
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
