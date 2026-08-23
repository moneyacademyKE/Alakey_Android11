package com.example.alakey.ui

import com.example.alakey.data.Chapter

/**
 * UI Data Specifications.
 * These are pure data classes that describe the "Shape" of the UI.
 * Components render Specs, not Entities.
 */

sealed interface AsyncOp {
    data object Idle : AsyncOp
    data object InFlight : AsyncOp
    data class Progress(val completedBytes: Long, val totalBytes: Long?) : AsyncOp
    data object Done : AsyncOp
    data class Failed(val message: String) : AsyncOp
}

data class PodcastRowSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val isDownloaded: Boolean = false,
    val isInQueue: Boolean = false,
    val progress: Float = 0f, // 0.0 - 1.0
    val remainingMs: Long = 0,
    val isSyncing: Boolean = false,
    val downloadOp: AsyncOp = AsyncOp.Idle
)

data class PlayerSpec(
    val title: String,
    val artist: String,
    val imageUrl: String,
    val isPlaying: Boolean,
    val isBuffering: Boolean = false,
    val currentMs: Long,
    val durationMs: Long,
    val bufferedMs: Long = 0,
    val speed: Float = 1.0f,
    val sleepTimerSeconds: Int = 0,
    val chapters: List<Chapter> = emptyList(),
    val currentChapterIndex: Int = -1,
    val dominantColor: Int = 0xFF00FFFF.toInt(),
    val vibrantColor: Int = 0xFF00FFFF.toInt(),
    val mutedColor: Int = 0xFF808080.toInt()
)
