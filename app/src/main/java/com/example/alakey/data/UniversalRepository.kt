package com.example.alakey.data

import com.example.alakey.domain.InformationModel
import com.example.alakey.system.DatabaseSystem
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.combine

@Singleton
class UniversalRepository @Inject constructor(
    private val dbSystem: DatabaseSystem,
    private val factsStore: FactStore,
    private val feeds: FeedRepository,
    private val downloads: DownloadRepository,
    private val playback: PlaybackStateRepository,
    private val observability: ObservabilityRepository
) {
    private val dao get() = dbSystem.db.dao()

    val facts = factsStore.facts
    val library = combine(dao.getAllPodcasts(), facts) { podcasts, allFacts -> hydrateWith(podcasts, allFacts) }
    val queue = combine(dao.getAllPodcasts(), facts) { podcasts, allFacts ->
        hydrateWith(podcasts, allFacts).filter { it.isInQueue }.sortedBy { it.queueOrder }
    }
    val inbox = combine(dao.getAllPodcasts(), facts) { podcasts, allFacts ->
        hydrateWith(podcasts, allFacts).filter { !it.isInQueue && it.progress == 0L }
    }

    suspend fun searchPodcasts(query: String) = feeds.searchPodcasts(query)
    suspend fun subscribe(url: String) = feeds.subscribe(url)
    suspend fun syncAll() = feeds.syncAll()

    suspend fun savePodcast(p: PodcastEntity) = dao.insertPodcast(p)
    suspend fun getPodcastById(id: String): PodcastEntity? = dao.getPodcastById(id)?.let { factsStore.hydrate(it) }
    suspend fun getPodcastsByTitle(title: String) = factsStore.hydrateAll(dao.getEpisodesByTitle(title))
    suspend fun unsubscribe(title: String) = dao.deleteByTitle(title)

    suspend fun updateProgress(id: String, progress: Long) = playback.updateProgress(id, progress)
    suspend fun updateLastPlayed(id: String, timestamp: Long) = playback.updateLastPlayed(id, timestamp)
    suspend fun addToQueue(id: String) = playback.addToQueue(id)
    suspend fun addToQueueNext(id: String) = playback.addToQueueNext(id)
    suspend fun removeFromQueue(id: String) = playback.removeFromQueue(id)
    suspend fun getLastPlayedPodcast() = playback.getLastPlayedPodcast()
    suspend fun getRadioCandidate() = playback.getRadioCandidate()
    suspend fun saveProgress(id: String, progress: Long) = updateProgress(id, progress)
    suspend fun savePalette(id: String, palette: PodcastPalette) = dao.updatePalette(id, palette)
    suspend fun markPlayed(p: PodcastEntity) = playback.markPlayed(p)
    suspend fun markOlderAsPlayed(ref: PodcastEntity) = playback.markOlderAsPlayed(ref)

    suspend fun runSmartDownloads() = downloads.runSmartDownloads()
    suspend fun downloadAudio(podcastId: String) = downloads.downloadAudio(podcastId)
    suspend fun deleteDownload(id: String) = downloads.deleteDownload(id)

    suspend fun assertFact(entityId: String, attribute: String, value: String) = factsStore.assert(entityId, attribute, value)
    suspend fun getFacts(entityId: String) = factsStore.getFacts(entityId)
    suspend fun getAttribute(entityId: String, attribute: String) = factsStore.getAttribute(entityId, attribute)
    suspend fun getAllFacts() = factsStore.getAllFacts()

    suspend fun getRecentLogs() = observability.getRecentLogs()
    suspend fun getLogsByType(type: String) = observability.getLogsByType(type)
    suspend fun getFailedLogs() = observability.getFailedLogs()
    suspend fun grepLogs(q: String) = observability.grepLogs(q)
    suspend fun rawQuery(sql: String) = observability.rawQuery(sql)

    private fun hydrateWith(podcasts: List<PodcastEntity>, facts: List<FactEntity>): List<PodcastEntity> {
        val factsByEntity = facts.groupBy { it.entityId }
        return podcasts.map { podcast ->
            InformationModel.hydrate(podcast, factsByEntity[podcast.id].orEmpty())
        }
    }
}
