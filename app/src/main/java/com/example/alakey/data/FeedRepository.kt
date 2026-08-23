package com.example.alakey.data

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.alakey.domain.NetworkLogic
import com.example.alakey.system.DatabaseSystem
import com.example.alakey.system.NetworkSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedRepository @Inject constructor(
    private val dbSystem: DatabaseSystem,
    private val networkSystem: NetworkSystem,
    private val networkCall: NetworkCall
) {
    private val syncMutex = Mutex()
    private val dao get() = dbSystem.db.dao()
    private val eventLogDao get() = dbSystem.db.eventLogDao()
    private val client get() = networkSystem.client

    suspend fun searchPodcasts(query: String): Result<List<ItunesSearchResult>> = networkCall.run {
        val request = Request.Builder().url("https://itunes.apple.com/search?term=$query&entity=podcast&media=podcast").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("iTunes search failed: ${response.code}")
            NetworkLogic.parseItunesResults(response.body?.string() ?: throw Exception("Empty response body"))
        }
    }

    // Single pass: feed fetch, parse, and insert are deterministic — retrying the whole
    // pipeline on a parse failure just re-downloads the feed three times (observed on-device).
    suspend fun subscribe(url: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val xmlContent = fetchValidFeed(url)
            val items = RssParser.parse(xmlContent, url)
            if (items.isEmpty()) throw Exception("No episodes found in feed")

            dao.insertEpisodes(items)
            eventLogDao.logEvent(EventLogEntity(type = "SUBSCRIBE_SUCCESS", payload = url, status = "COMPLETED", timestamp = System.currentTimeMillis()))
            Log.d("FeedRepository", "Subscribed to $url, ${items.size} items found.")
            true
        }
    }

    suspend fun syncAll() = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            dao.getSubscribedFeeds().forEach { url -> subscribe(url).exceptionOrNull()?.printStackTrace() }
        }
    }

    /** Podcasting 2.0 chapters JSON — best effort; missing/broken files yield an empty list. */
    suspend fun fetchChapters(url: String): List<Chapter> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                ChapterParser.parse(response.body?.string().orEmpty())
            }
        }.getOrDefault(emptyList())
    }

    private suspend fun fetchValidFeed(url: String): String {
        val direct = runCatching { fetchDirect(url) }.getOrNull()
        if (direct.isValidXmlFeed()) return direct.orEmpty()

        val proxied = runCatching { fetchWithProxy(url) }.getOrNull()
        if (proxied.isValidXmlFeed()) return proxied.orEmpty()

        throw Exception("Failed to fetch valid feed content")
    }

    private suspend fun fetchDirect(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Feed fetch failed: ${response.code}")
            response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    private suspend fun fetchWithProxy(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("https://api.allorigins.win/get?url=$url").build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Proxy fetch failed: ${response.code}")
            NetworkLogic.extractProxyContent(response.body?.string() ?: throw Exception("Empty proxy response"))
        }
    }

    private fun String?.isValidXmlFeed(): Boolean = looksLikeFeed(this)

    companion object {
        @VisibleForTesting
        internal fun looksLikeFeed(content: String?): Boolean {
            val trimmed = content?.trim().orEmpty()
            if (!trimmed.startsWith("<")) return false
            val head = trimmed.take(64).lowercase()
            // Error/block pages come as both "<!DOCTYPE html..." and bare "<html..."
            return !head.startsWith("<!doctype html") && !head.startsWith("<html")
        }
    }
}
