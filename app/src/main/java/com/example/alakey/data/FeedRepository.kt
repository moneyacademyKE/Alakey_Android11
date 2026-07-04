package com.example.alakey.data

import android.util.Log
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

    suspend fun subscribe(url: String): Result<Boolean> = networkCall.run {
        val xmlContent = fetchValidFeed(url)
        val items = RssParser.parse(xmlContent, url)
        if (items.isEmpty()) throw Exception("No episodes found in feed")

        dao.insertEpisodes(items)
        eventLogDao.logEvent(EventLogEntity(type = "SUBSCRIBE_SUCCESS", payload = url, status = "COMPLETED", timestamp = System.currentTimeMillis()))
        Log.d("FeedRepository", "Subscribed to $url, ${items.size} items found.")
        true
    }

    suspend fun syncAll() = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            dao.getSubscribedFeeds().forEach { url -> subscribe(url).exceptionOrNull()?.printStackTrace() }
        }
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
            response.body?.string() ?: throw Exception("Empty response body")
        }
    }

    private suspend fun fetchWithProxy(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("https://api.allorigins.win/get?url=$url").build()).execute().use { response ->
            NetworkLogic.extractProxyContent(response.body?.string() ?: throw Exception("Empty proxy response"))
        }
    }

    private fun String?.isValidXmlFeed(): Boolean {
        val trimmed = this?.trim().orEmpty()
        return trimmed.startsWith("<") && !trimmed.startsWith("<!DOCTYPE html", ignoreCase = true)
    }
}
