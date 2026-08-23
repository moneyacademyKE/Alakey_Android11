package com.example.alakey.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Feed-data access only. Episode runtime state (progress, queue, downloads,
 * last-played) lives in the facts table — see FactStore / InformationModel.
 */
@Dao
interface PodcastDao {
    @Query("SELECT * FROM podcasts ORDER BY pubDate DESC")
    fun getAllPodcasts(): Flow<List<PodcastEntity>>

    @Query("SELECT * FROM podcasts WHERE id = :id LIMIT 1")
    suspend fun getPodcastById(id: String): PodcastEntity?

    @Query("SELECT DISTINCT feedUrl FROM podcasts WHERE feedUrl != ''")
    suspend fun getSubscribedFeeds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodes(list: List<PodcastEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPodcast(p: PodcastEntity)

    @Query("SELECT * FROM podcasts WHERE title = :title")
    suspend fun getEpisodesByTitle(title: String): List<PodcastEntity>

    @Query("DELETE FROM podcasts WHERE title = :title")
    suspend fun deleteByTitle(title: String)

    @Query("UPDATE podcasts SET palette = :palette WHERE id = :id")
    suspend fun updatePalette(id: String, palette: PodcastPalette)
}
