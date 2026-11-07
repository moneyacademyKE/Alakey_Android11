package com.example.alakey.data

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v13 → v14 (M14): guid-less episode ids were 32-bit String hashCodes —
 * collisions hit PodcastDao's INSERT IGNORE and episodes silently never
 * entered the library. v14 re-derives every legacy id through
 * [EpisodeIds.derive] (SHA-256, 64-bit). Ids are the only thing that
 * changes; both tables that reference them are rewritten in the same
 * transaction:
 *   podcasts.id     — the row's identity
 *   facts.entityId  — progress, queue, downloads: user state
 *
 * A rewrite only happens for rows we can PROVE are legacy: the stored id
 * must equal the v13 hashCode of its own feedUrl+audioUrl. Unknown rows
 * keep their id — stable, merely unfashionable — rather than risk a wrong
 * rewrite. New 16-hex ids can never collide with guid ids (which embed the
 * feedUrl), so the PK update is safe.
 */
private const val TAG = "Migrations"

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val rewrites = mutableMapOf<String, String>() // oldId -> newId
        db.query("SELECT id, feedUrl, audioUrl FROM podcasts").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                if (!EpisodeIds.isLegacyHashId(id)) continue
                val feedUrl = cursor.getString(1).orEmpty()
                val audioUrl = cursor.getString(2).orEmpty()
                if ((feedUrl + audioUrl).hashCode().toString() != id) continue
                rewrites[id] = EpisodeIds.derive(feedUrl, audioUrl)
            }
        }
        if (rewrites.isEmpty()) return

        val updatePodcast = db.compileStatement("UPDATE podcasts SET id = ? WHERE id = ?")
        val updateFacts = db.compileStatement("UPDATE facts SET entityId = ? WHERE entityId = ?")
        rewrites.forEach { (oldId, newId) ->
            updatePodcast.bindString(1, newId)
            updatePodcast.bindString(2, oldId)
            if (updatePodcast.executeUpdateDelete() > 0) {
                updateFacts.bindString(1, newId)
                updateFacts.bindString(2, oldId)
                updateFacts.executeUpdateDelete()
            }
        }
        Log.i(TAG, "M14: re-derived ${rewrites.size} guid-less episode id(s) to 64-bit")
    }
}

/**
 * v14 → v15 (L72): drops `index_facts_attribute`. Its only consumer,
 * FactDao.getFactsUsingAttribute, was deleted in round 3 — every surviving
 * facts query filters on entityId (PK leading column / its index) or groups
 * on (entityId, attribute). The index was pure write-path cost: maintained
 * on every fact assert for a lookup nobody made.
 */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_facts_attribute`")
    }
}
