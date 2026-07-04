package com.example.alakey.ui

/**
 * UI Data Specifications.
 * These are pure data classes that describe the "Shape" of the UI.
 * Components render Specs, not Entities.
 */

data class PodcastRowSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val isDownloaded: Boolean = false,
    val isInQueue: Boolean = false,
    val progress: Float = 0f, // 0.0 - 1.0
    val isSyncing: Boolean = false
)

data class PlayerSpec(
    val title: String,
    val artist: String,
    val imageUrl: String,
    val isPlaying: Boolean,
    val currentMs: Long,
    val durationMs: Long,
    val speed: Float = 1.0f,
    val amplitude: Float = 0f,
    val sleepTimerSeconds: Int = 0,
    val dominantColor: Int = 0xFF00FFFF.toInt(),
    val vibrantColor: Int = 0xFF00FFFF.toInt(),
    val mutedColor: Int = 0xFF808080.toInt()
)
