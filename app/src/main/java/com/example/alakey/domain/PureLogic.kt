package com.example.alakey.domain

import com.example.alakey.data.PodcastEntity
import java.util.Locale
import java.text.SimpleDateFormat

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
