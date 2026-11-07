package com.example.alakey.domain

import com.example.alakey.data.PodcastEntity
import java.util.Locale
import java.text.SimpleDateFormat

/**
 * M69: queue positions are ordinals, not timestamps. A wall-clock stamp tied
 * queue order to the date: same-millisecond adds tied, a backward clock step
 * reordered the user's explicit "play next", and the empty-queue seed anchored
 * the ordinal space at ~1.7e12. Existing installs keep their stored orders —
 * append/playNext are computed from the data present, never from the clock.
 */
object QueueOrders {
    /** Append lands after every existing entry. */
    fun append(existingOrders: List<Long>): Long = (existingOrders.maxOrNull() ?: 0L) + 1L

    /** "Play next" lands before every existing entry. */
    fun playNext(existingOrders: List<Long>): Long = (existingOrders.minOrNull() ?: 0L) - 1L
}

/**
 * Pure Domain Logic.
 * Contains only deterministic functions. No IO, no State.
 */
object PureLogic {

    fun determineDownloadCandidates(library: List<PodcastEntity>): List<String> {
        val candidates = mutableListOf<String>()
        val feeds = library.groupBy { it.feedUrl }

        for ((_, episodes) in feeds) {
            if (episodes.isEmpty()) continue

            // Extract policy from the latest episode (proxy for Feed config)
            val latestEp = latestByDate(episodes)
            val policy = latestEp?.downloadPolicy ?: "latest"

            when (policy) {
                "latest" -> {
                    val latest = latestByDate(episodes)
                    if (latest != null && !latest.isDownloaded) {
                        candidates.add(latest.id)
                    }
                }
                "oldest_unplayed" -> {
                    val nextUp = episodes
                        .filter { it.progress == 0L && !it.isDownloaded }
                        .minByOrNull { it.pubDateMs() }
                    if (nextUp != null) {
                        candidates.add(nextUp.id)
                    }
                }
                "none" -> { /* No-op */ }
            }
        }
        return candidates
    }

    fun determineArchiveCandidates(ref: PodcastEntity, allEpisodes: List<PodcastEntity>): List<String> {
        val refDate = parseDateOrNull(ref.pubDate) ?: return emptyList()

        return allEpisodes.filter {
            // Filter: Older than ref, not the ref itself, and not already played (though archiving played is fine too,
            // but the feature is "Mark Older as Played", implying they are unplayed).
            // M56: undated episodes have unknown age — the 0L sentinel made them "older than
            // any dated ref" and bulk-marked them played; null excludes them.
            val d = parseDateOrNull(it.pubDate) ?: return@filter false
            d < refDate && it.id != ref.id && it.progress < it.duration
        }.map { it.id }
    }

    /**
     * M56: null IS "unknown date" — the 0L sentinel was indistinguishable from
     * 1970 and decided auto-download ("latest" = DB insertion order when all
     * parsed to 0), bulk archive (undated = "older than everything"), and
     * library ordering. Strict (non-lenient) parsing so impossible dates like
     * "45 Jan" fail instead of silently rolling into February.
     */
    fun parseDateOrNull(date: String): Long? = try {
        strictFormat("EEE, dd MMM yyyy HH:mm:ss Z").parse(date)?.time
            ?: strictFormat("EEE, dd MMM yyyy HH:mm:ss z").parse(date)?.time
    } catch (e: Exception) {
        null
    }

    private fun strictFormat(pattern: String): SimpleDateFormat =
        SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }

    /** M56 helper: latest dated episode — undated entries never win a date-based pick. */
    private fun latestByDate(episodes: List<PodcastEntity>): PodcastEntity? =
        episodes.mapNotNull { e -> parseDateOrNull(e.pubDate)?.let { e to it } }
            .maxByOrNull { it.second }?.first

    /** M56 helper: undated sorts as unknown (never minimal) for oldest-picks. */
    private fun PodcastEntity.pubDateMs(): Long = parseDateOrNull(pubDate) ?: Long.MAX_VALUE
}
