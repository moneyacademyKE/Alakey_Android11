package com.example.alakey.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import com.example.alakey.system.DatabaseSystem
import com.example.alakey.system.NetworkSystem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UniversalRepository @Inject constructor(
    private val dbSystem: DatabaseSystem,
    private val networkSystem: NetworkSystem,
    @ApplicationContext private val context: Context
) {
    private val syncMutex = kotlinx.coroutines.sync.Mutex()
    private val dao get() = dbSystem.db.dao()
    private val eventLogDao get() = dbSystem.db.eventLogDao()
    private val factDao get() = dbSystem.db.factDao()
    private val client get() = networkSystem.client

    val library = dao.getAllPodcasts()
    val inbox = dao.getInbox()
    val queue = dao.getQueue()
    val facts = factDao.getAllFactsFlow()

    // Java 25 / Loom readiness: This dispatcher should eventually act on Virtual Threads.
    // val LoomDispatcher = Executors.newVirtualThreadPerTaskExecutor().asCoroutineDispatcher()
    private val ioDispatcher = Dispatchers.IO 

    private suspend fun <T> safeApiCall(retries: Int = 3, initialDelay: Long = 2000, apiCall: suspend () -> T): Result<T> {
        return withContext(ioDispatcher) {
            var currentDelay = initialDelay
            repeat(retries - 1) {
                try {
                    return@withContext Result.success(apiCall())
                } catch (e: Exception) {
                    Log.w("UniversalRepository", "API Call Failed. Retrying in ${currentDelay}ms", e)
                    delay(currentDelay)
                    currentDelay *= 2
                }
            }
            try {
                Result.success(apiCall())
            } catch (e: Exception) {
                Log.e("UniversalRepository", "API Call Failed after retries", e)
                Result.failure(e)
            }
        }
    }

    suspend fun searchPodcasts(query: String): Result<List<ItunesSearchResult>> = safeApiCall {
        val request = Request.Builder().url("https://itunes.apple.com/search?term=$query&entity=podcast&media=podcast").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("iTunes search failed: ${response.code}")
            val json = response.body?.string() ?: throw Exception("Empty response body")
            com.example.alakey.domain.NetworkLogic.parseItunesResults(json)
        }
    }

    suspend fun subscribe(url: String): Result<Boolean> = safeApiCall {
        var xmlContent: String? = null
        
        try {
            Log.d("UniversalRepository", "Attempting direct fetch for: $url")
            xmlContent = fetchDirect(url)
        } catch (e: Exception) {
            Log.w("UniversalRepository", "Direct fetch failed: ${e.message}")
        }

        if (xmlContent == null || xmlContent.trim().startsWith("<!DOCTYPE html", ignoreCase = true) || !xmlContent.trim().startsWith("<")) {
            try {
                Log.d("UniversalRepository", "Attempting proxy fetch for: $url")
                xmlContent = fetchWithProxy(url)
            } catch (e: Exception) {
                Log.w("UniversalRepository", "Proxy fetch failed: ${e.message}")
            }
        }

        if (xmlContent == null || xmlContent.trim().startsWith("<!DOCTYPE html", ignoreCase = true) || !xmlContent.trim().startsWith("<")) {
            throw Exception("Failed to fetch valid feed content")
        }
        
        val items = RssParser.parse(xmlContent, url)
        if (items.isNotEmpty()) {
            dao.insertEpisodes(items)
            eventLogDao.logEvent(EventLogEntity(type = "SUBSCRIBE_SUCCESS", payload = url, status = "COMPLETED", timestamp = System.currentTimeMillis()))
            Log.d("UniversalRepository", "Successfully subscribed to $url, ${items.size} items found.")
            true
        } else {
            throw Exception("No episodes found in feed")
        }
    }

    suspend fun syncAll() = syncMutex.withLock {
        withContext(Dispatchers.IO) {
        dao.getSubscribedFeeds().forEach { url -> 
            try {
                subscribe(url)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    }

    suspend fun savePodcast(p: PodcastEntity) = dao.insertPodcast(p)
    suspend fun getPodcastById(id: String): PodcastEntity? {
        val base = dao.getPodcastById(id) ?: return null
        return hydrate(base)
    }

    private suspend fun hydrate(base: PodcastEntity): PodcastEntity {
        val facts = factDao.getLatestFacts(base.id)
        return com.example.alakey.domain.InformationModel.hydrate(base, facts)
    }
    suspend fun getPodcastsByTitle(title: String) = dao.getEpisodesByTitle(title)
    suspend fun updateProgress(id: String, progress: Long) {
        assertFact(id, com.example.alakey.domain.InformationModel.ATTR_PROGRESS, progress.toString())
        assertFact(id, com.example.alakey.domain.InformationModel.ATTR_LAST_PLAYED, System.currentTimeMillis().toString())
    }

    suspend fun updateLastPlayed(id: String, timestamp: Long) = 
        assertFact(id, com.example.alakey.domain.InformationModel.ATTR_LAST_PLAYED, timestamp.toString())

    private suspend fun fetchWithProxy(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.allorigins.win/get?url=$url").build()
        client.newCall(request).execute().use { response ->
            val json = response.body?.string() ?: throw Exception("Empty proxy response")
            com.example.alakey.domain.NetworkLogic.extractProxyContent(json)
        }
    }

    private suspend fun fetchDirect(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    suspend fun runSmartDownloads() {
        withContext(Dispatchers.IO) {
            val library: List<PodcastEntity> = dao.getAllPodcasts().first()
            val candidates = com.example.alakey.domain.PureLogic.determineDownloadCandidates(library)
            
            candidates.forEach { id ->
                try {
                    downloadAudio(id)
                } catch (e: Exception) {
                    Log.e("UniversalRepository", "Smart Download failed for $id", e)
                }
            }
        }
    }

    suspend fun downloadAudio(podcastId: String): Result<String> = safeApiCall {
         val podcast = dao.getPodcastById(podcastId) ?: throw Exception("Podcast not found")
         if (podcast.audioUrl.isEmpty()) throw Exception("No audio URL")
         
         val request = Request.Builder().url(podcast.audioUrl).build()
         client.newCall(request).execute().use { response ->
             if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")
             
             val file = File(context.filesDir, "${podcastId}.mp3")
             val body = response.body ?: throw Exception("Empty response body")
             body.byteStream().use { inputStream ->
                 FileOutputStream(file).use { output ->
                     inputStream.copyTo(output)
                 }
             }
             dao.updateAudioPath(podcastId, file.absolutePath)
             assertFact(podcastId, com.example.alakey.domain.InformationModel.ATTR_DOWNLOADED, "true")
             file.absolutePath
         }
    }

    suspend fun unsubscribe(title: String) = withContext(Dispatchers.IO) {
        val episodes = dao.getEpisodesByTitle(title)
        episodes.forEach { p ->
            if (p.isDownloaded && p.audioUrl.startsWith("/")) {
                 val file = File(p.audioUrl)
                 if (file.exists()) file.delete()
            }
        }
        dao.deleteByTitle(title)
    }

    suspend fun addToQueue(id: String) {
        assertFact(id, com.example.alakey.domain.InformationModel.ATTR_IN_QUEUE, "true")
        assertFact(id, com.example.alakey.domain.InformationModel.ATTR_QUEUE_ORDER, System.currentTimeMillis().toString())
    }

    suspend fun removeFromQueue(id: String) {
        assertFact(id, com.example.alakey.domain.InformationModel.ATTR_IN_QUEUE, "false")
    }

    suspend fun getLastPlayedPodcast(): PodcastEntity? {
         val p = dao.getLastPlayedPodcast() ?: return null
         return hydrate(p)
    }

    suspend fun getRadioCandidate(): PodcastEntity? {
        val p = dao.getRadioCandidate() ?: return null
        return hydrate(p)
    }

    suspend fun saveProgress(id: String, progress: Long) = updateProgress(id, progress)
    suspend fun savePalette(id: String, palette: PodcastPalette) = dao.updatePalette(id, palette)

    suspend fun markPlayed(p: PodcastEntity) {
        updateProgress(p.id, p.duration)
        removeFromQueue(p.id)
    }

    suspend fun deleteDownload(id: String) {
        withContext(Dispatchers.IO) {
            val p = getPodcastById(id) ?: return@withContext
            if (p.isDownloaded && p.audioUrl.isNotEmpty()) {
                val file = File(p.audioUrl)
                if (file.exists()) file.delete()
            }
            assertFact(id, com.example.alakey.domain.InformationModel.ATTR_DOWNLOADED, "false")
            dao.setDownloaded(id, false) // Still update core for now to keep UI flow simpler
        }
    }

    suspend fun markOlderAsPlayed(ref: PodcastEntity) {
        withContext(Dispatchers.IO) {
            val episodes = dao.getEpisodesByTitle(ref.title)
            val toMark = com.example.alakey.domain.PureLogic.determineArchiveCandidates(ref, episodes)

            if (toMark.isNotEmpty()) {
                toMark.forEach { id -> 
                    val ep = dao.getPodcastById(id)
                    if (ep != null) markPlayed(ep)
                }
            }
        }
    }
    
    // --- Phase 5: Information Model (Facts) ---
    suspend fun assertFact(entityId: String, attribute: String, value: String) {
        // In a true Datomic system, we accumulate. Here, we replace for "Simplicity" (Last-Write-Wins),
        // or we append with new TX? 
        // FactEntity PK is (entityId, attribute, tx). So we can have multiple values over time.
        // However, queries need to select MAX(tx).
        // For this Phase 5 implementation, let's just insert.
        factDao.insert(FactEntity(entityId, attribute, value))
    }
    
    suspend fun getFacts(entityId: String): List<FactEntity> = factDao.getFactsUsingEntity(entityId)
    
    suspend fun getAttribute(entityId: String, attribute: String): String? {
         // Naive "Latest" query: Filter in memory.
         // In prod, use window function in SQL.
         return factDao.getFactsUsingEntity(entityId)
             .filter { it.attribute == attribute }
             .maxByOrNull { it.tx }
             ?.value
    }

    // --- Phase 7: Observability (Logs) ---
    suspend fun getRecentLogs() = eventLogDao.getRecentEvents()
    suspend fun getLogsByType(type: String) = eventLogDao.getEventsByType(type)
    suspend fun getFailedLogs() = eventLogDao.getFailedEvents()
    suspend fun grepLogs(q: String) = eventLogDao.grepLogs(q)

    suspend fun getAllFacts(): List<FactEntity> = factDao.getAllFacts()

    suspend fun rawQuery(sql: String): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val cursor = dbSystem.db.query(sql, null)
        val results = mutableListOf<Map<String, Any?>>()
        cursor.use { c ->
            val columnNames = c.columnNames
            while (c.moveToNext()) {
                val map = mutableMapOf<String, Any?>()
                columnNames.forEachIndexed { index, name ->
                    map[name] = when (c.getType(index)) {
                        android.database.Cursor.FIELD_TYPE_NULL -> null
                        android.database.Cursor.FIELD_TYPE_INTEGER -> c.getLong(index)
                        android.database.Cursor.FIELD_TYPE_FLOAT -> c.getDouble(index)
                        android.database.Cursor.FIELD_TYPE_STRING -> c.getString(index)
                        android.database.Cursor.FIELD_TYPE_BLOB -> c.getBlob(index)
                        else -> c.getString(index)
                    }
                }
                results.add(map)
            }
        }
        results
    }
}
