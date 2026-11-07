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
        // M7: built via HttpUrl.Builder — a term containing '&'/'#'/UTF-8
        // previously truncated or broke the whole request.
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("itunes.apple.com")
            .addPathSegment("search")
            .addQueryParameter("term", query)
            .addQueryParameter("entity", "podcast")
            .addQueryParameter("media", "podcast")
            .build()
        val request = Request.Builder().url(url).build()
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

    /** M67: per-feed failures are data — the worker decides retryability from this. */
    data class SyncOutcome(val feeds: Int, val failures: Int) {
        val allFailed: Boolean get() = feeds > 0 && failures == feeds
    }

    suspend fun syncAll(): SyncOutcome = syncMutex.withLock {
        withContext(Dispatchers.IO) {
            val feeds = dao.getSubscribedFeeds()
            var failures = 0
            feeds.forEach { url ->
                // L4: per-feed failures belong in the event log the app can
                // query, not a printStackTrace nobody reads.
                subscribe(url).exceptionOrNull()?.let { error ->
                    failures++
                    Log.w("FeedRepository", "Feed sync failed: $url", error)
                    eventLogDao.logEvent(
                        EventLogEntity(type = "SYNC_FEED", payload = url, status = "FAILED", timestamp = System.currentTimeMillis())
                    )
                }
            }
            // M12: keep the event log bounded — one purge per sync, after writes.
            eventLogDao.purgeOlderThan(System.currentTimeMillis() - EventLog.RETENTION_MS)
            SyncOutcome(feeds = feeds.size, failures = failures)
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
        // M2: the third-party CORS-proxy fallback is gone — it leaked every
        // feed URL to api.allorigins.win on direct-fetch failure. A failed
        // direct fetch now surfaces as a logged sync failure instead.
        val direct = runCatching { fetchDirect(url) }.getOrNull()
        if (direct.isValidXmlFeed()) return direct.orEmpty()

        throw Exception("Failed to fetch valid feed content")
    }

    private suspend fun fetchDirect(url: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Feed fetch failed: ${response.code}")
            response.body?.string() ?: throw Exception("Empty response body")
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
