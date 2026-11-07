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
            val latestEp = episodes.maxByOrNull { parseDate(it.pubDate) }
            val policy = latestEp?.downloadPolicy ?: "latest"
            
            when (policy) {
                "latest" -> {
                    val latest = episodes.maxByOrNull { parseDate(it.pubDate) }
                    if (latest != null && !latest.isDownloaded) {
                        candidates.add(latest.id)
                    }
                }
                "oldest_unplayed" -> {
                    val nextUp = episodes.filter { it.progress == 0L && !it.isDownloaded }
                                         .minByOrNull { parseDate(it.pubDate) }
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
        val refDate = parseDate(ref.pubDate)
        
        return allEpisodes.filter {
            val d = parseDate(it.pubDate)
            // Filter: Older than ref, not the ref itself, and not already played (though archiving played is fine too, 
            // but the feature is "Mark Older as Played", implying they are unplayed).
            d < refDate && it.id != ref.id && it.progress < it.duration
        }.map { it.id }
    }

    fun parseDate(date: String): Long {
         try {
             // Try standard RSS format
             return SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).parse(date)?.time 
                 ?: SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).parse(date)?.time 
                 ?: 0L
         } catch (e: Exception) {
             return 0L
         }
    }
}
