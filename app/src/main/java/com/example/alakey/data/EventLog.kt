package com.example.alakey.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Entity(tableName = "event_log")
data class EventLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // "SYNC_FEED", "DOWNLOAD_EPISODE", "PLAY_EPISODE"
    val payload: String, // JSON or simple ID
    val status: String, // "PENDING", "COMPLETED", "FAILED"
    val timestamp: Long
)

@Dao
interface EventLogDao {
    @Insert
    suspend fun logEvent(event: EventLogEntity)

    // M12: event_log grew unbounded — one row per feed per sync, forever.
    // Retention: callers sweep entries older than the cutoff.
    @Query("DELETE FROM event_log WHERE timestamp < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)

    @Query("SELECT * FROM event_log ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentEvents(): List<EventLogEntity>

    @Query("SELECT * FROM event_log WHERE type = :type ORDER BY timestamp DESC LIMIT 50")
    suspend fun getEventsByType(type: String): List<EventLogEntity>

    @Query("SELECT * FROM event_log WHERE status = 'FAILED' ORDER BY timestamp DESC LIMIT 50")
    suspend fun getFailedEvents(): List<EventLogEntity>

    // M12: '%'/'_' in the user's query acted as wildcards — escape them.
    @Query("SELECT * FROM event_log WHERE payload LIKE '%' || :query || '%' ESCAPE '\\' ORDER BY timestamp DESC LIMIT 50")
    suspend fun grepLogs(query: String): List<EventLogEntity>
}

object EventLog {
    /** Event-log retention window. */
    const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000

    /**
     * Escapes LIKE wildcards so a literal '%', '_' or '\' in [query] matches
     * itself under the ESCAPE '\' clause used by [EventLogDao.grepLogs].
     */
    fun escapeLike(query: String): String =
        query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
