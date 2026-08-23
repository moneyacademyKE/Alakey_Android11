package com.example.alakey.ui

/** Pure playback events reduced into PlaybackState. */
sealed interface PlaybackEvent {
    data class IsPlayingChanged(val isPlaying: Boolean) : PlaybackEvent
    data class IsBufferingChanged(val isBuffering: Boolean) : PlaybackEvent
    data class PositionUpdated(val position: Long, val duration: Long, val buffered: Long) : PlaybackEvent
    data class MediaChanged(val mediaId: String?, val duration: Long) : PlaybackEvent
    data class SpeedChanged(val speed: Float) : PlaybackEvent
}
