package com.example.alakey.data

import com.example.alakey.domain.InformationModel
import com.example.alakey.domain.PureLogic
import com.example.alakey.system.DatabaseSystem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class PlaybackStateRepository @Inject constructor(
    private val dbSystem: DatabaseSystem,
    private val factStore: FactStore
) {
    private val dao get() = dbSystem.db.dao()

    suspend fun updateProgress(id: String, progress: Long) {
        factStore.assert(id, InformationModel.ATTR_PROGRESS, progress.toString())
        updateLastPlayed(id, System.currentTimeMillis())
    }

    suspend fun updateLastPlayed(id: String, timestamp: Long) {
        factStore.assert(id, InformationModel.ATTR_LAST_PLAYED, timestamp.toString())
    }

    suspend fun addToQueue(id: String) {
        factStore.assert(id, InformationModel.ATTR_IN_QUEUE, "true")
        factStore.assert(id, InformationModel.ATTR_QUEUE_ORDER, System.currentTimeMillis().toString())
    }

    suspend fun addToQueueNext(id: String) {
        val queue = factStore.hydrateAll(dao.getAllPodcasts().firstValue())
            .filter { it.isInQueue && it.id != id }
            .sortedBy { it.queueOrder }
        val firstOrder = queue.firstOrNull()?.queueOrder ?: System.currentTimeMillis()
        factStore.assert(id, InformationModel.ATTR_IN_QUEUE, "true")
        factStore.assert(id, InformationModel.ATTR_QUEUE_ORDER, (firstOrder - 1).toString())
    }

    suspend fun removeFromQueue(id: String) {
        factStore.assert(id, InformationModel.ATTR_IN_QUEUE, "false")
    }

    suspend fun markPlayed(podcast: PodcastEntity) {
        updateProgress(podcast.id, podcast.duration)
        removeFromQueue(podcast.id)
    }

    suspend fun markOlderAsPlayed(ref: PodcastEntity) {
        withContext(Dispatchers.IO) {
            val hydrated = factStore.hydrateAll(dao.getEpisodesByTitle(ref.title))
            PureLogic.determineArchiveCandidates(ref, hydrated).forEach { id ->
                dao.getPodcastById(id)?.let { markPlayed(factStore.hydrate(it)) }
            }
        }
    }

    suspend fun getLastPlayedPodcast(): PodcastEntity? {
        val candidates = factStore.hydrateAll(dao.getAllPodcasts().firstValue())
        return candidates.maxByOrNull { it.lastPlayed }?.takeIf { it.lastPlayed > 0 }
    }

    suspend fun getRadioCandidate(): PodcastEntity? {
        val candidates = factStore.hydrateAll(dao.getAllPodcasts().firstValue())
        return candidates.filter { !it.isInQueue && it.progress == 0L }.randomOrNull()
    }
}
