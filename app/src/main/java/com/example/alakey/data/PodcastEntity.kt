package com.example.alakey.data

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

/**
 * Immutable value type representing a Podcast Episode at a specific point in time.
 * "State is a value." — Rich Hickey
 *
 * Persisted fields are feed-derived values ONLY. Volatile runtime state (progress,
 * queue membership, download state, last-played) is not stored here — it lives in
 * the facts table and InformationModel.hydrate fills it in. One name, one meaning:
 * these fields mean the same thing everywhere because the DB cannot lie about them.
 */
@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val id: String,
    val title: String,
    val episodeTitle: String,
    val description: String,
    val imageUrl: String,
    val audioUrl: String,
    val feedUrl: String = "",
    val duration: Long = 0,
    val pubDate: String = "",
    val chapters: List<Chapter> = emptyList(),
    val palette: PodcastPalette? = null,
    /** Podcasting 2.0 chapters JSON url — a feed-derived value, not runtime state. */
    val chaptersUrl: String? = null,
    /** Feed-declared download policy; PureLogic smart downloads read it. */
    val downloadPolicy: String = "latest"
) {
    // Hydrated runtime state (facts table via InformationModel.hydrate). Never
    // persisted. Deliberately outside the constructor: data-class equality, hashCode
    // and copy() see only feed data — the episode's identity — never its state.
    @Ignore var progress: Long = 0
    @Ignore var isDownloaded: Boolean = false
    @Ignore var isInQueue: Boolean = false
    @Ignore var queueOrder: Long = 0
    @Ignore var lastPlayed: Long = 0
}

data class ItunesSearchResult(
    val collectionName: String,
    val feedUrl: String,
    val artworkUrl100: String,
    val artistName: String = ""
)
data class ItunesSearchResponse(val results: List<ItunesSearchResult>)
