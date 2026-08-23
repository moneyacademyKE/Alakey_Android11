package com.example.alakey.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PodcastEntity::class, EventLogEntity::class, FactEntity::class], version = 13, exportSchema = false)
@androidx.room.TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): PodcastDao
    abstract fun eventLogDao(): EventLogDao
    abstract fun factDao(): FactDao
}

class Converters {
    @androidx.room.TypeConverter
    fun fromChapters(value: List<Chapter>): String = com.google.gson.Gson().toJson(value)
    
    @androidx.room.TypeConverter
    fun toChapters(value: String): List<Chapter> {
        val type = object : com.google.gson.reflect.TypeToken<List<Chapter>>() {}.type
        return com.google.gson.Gson().fromJson(value, type)
    }

    @androidx.room.TypeConverter
    fun fromPalette(value: PodcastPalette?): String = if (value == null) "" else com.google.gson.Gson().toJson(value)

    @androidx.room.TypeConverter
    fun toPalette(value: String): PodcastPalette? {
        if (value.isEmpty()) return null
        return com.google.gson.Gson().fromJson(value, PodcastPalette::class.java)
    }
}
