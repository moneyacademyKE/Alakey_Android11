package com.example.alakey.data

import androidx.room.Database
import androidx.room.RoomDatabase

object RoomSchema {
    // v14 (M14): guid-less episode ids re-derived to 64-bit — see Migrations.kt.
    const val VERSION = 14
}

@Database(
    entities = [PodcastEntity::class, EventLogEntity::class, FactEntity::class],
    version = RoomSchema.VERSION,
    // C1: schemas are the migration source of truth — export them from v13 on.
    exportSchema = true
)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PodcastDao
    abstract fun eventLogDao(): EventLogDao
    abstract fun factDao(): FactDao

    companion object {
        const val SCHEMA_VERSION = RoomSchema.VERSION
    }
}

class Converters {
    // L12: one Gson, not a fresh instance per conversion.
    private val gson = com.google.gson.Gson()

    @androidx.room.TypeConverter
    fun fromChapters(value: List<Chapter>): String = gson.toJson(value)

    // L12: Gson.fromJson returns null for the stored string "null" and throws
    // on malformed JSON — the non-null return type turned that into a crash
    // on every getAllPodcasts emission. Corrupt rows now degrade to empty.
    @androidx.room.TypeConverter
    fun toChapters(value: String): List<Chapter> {
        if (value.isBlank() || value == "null") return emptyList()
        val type = object : com.google.gson.reflect.TypeToken<List<Chapter>>() {}.type
        return runCatching { gson.fromJson<List<Chapter>>(value, type) }.getOrDefault(emptyList())
    }

    @androidx.room.TypeConverter
    fun fromPalette(value: PodcastPalette?): String = if (value == null) "" else gson.toJson(value)

    @androidx.room.TypeConverter
    fun toPalette(value: String): PodcastPalette? {
        if (value.isBlank() || value == "null") return null
        return runCatching { gson.fromJson(value, PodcastPalette::class.java) }.getOrNull()
    }
}
