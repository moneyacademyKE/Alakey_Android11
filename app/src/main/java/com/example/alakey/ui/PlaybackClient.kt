package com.example.alakey.ui

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.alakey.data.PodcastEntity
import com.example.alakey.service.AudioService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single projection surface over the MediaSession player.
 * The session IS the player: queue, position, and speed live in the controller;
 * the UI only reduces events into state.
 */
@Singleton
class PlaybackClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class PlaybackState(
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val duration: Long = 1L,
        val currentPosition: Long = 0L,
        val bufferedPosition: Long = 0L,
        val currentMediaId: String? = null,
        val playbackSpeed: Float = 1.0f
    )

    data class DesiredState(
        val isPlaying: Boolean = false,
        val mediaItem: MediaItem? = null,
        val seekPosition: Long? = null,
        val playbackSpeed: Float = 1.0f
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _desiredState = MutableStateFlow(DesiredState(playbackSpeed = persistedSpeed()))

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null

    // Sleep Timer
    private val _sleepTimerSeconds = MutableStateFlow(0)
    val sleepTimerSeconds: StateFlow<Int> = _sleepTimerSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null
    private var initialSleepDuration = 0

    // Smart Resume: tracked at the listener so EVERY pause source counts
    // (notification, headset, lockscreen — not just in-app taps).
    private var lastPauseTime = 0L

    // Sleep-at-end-of-episode mode: pause when the current episode ends (no duration countdown).
    private val _sleepAtEpisodeEnd = MutableStateFlow(false)
    val sleepAtEpisodeEnd: StateFlow<Boolean> = _sleepAtEpisodeEnd.asStateFlow()

    init {
        connect()
        scope.launch {
            playbackEnded.collect { if (_sleepAtEpisodeEnd.value) { _sleepAtEpisodeEnd.value = false; pause() } }
        }
    }

    private fun connect() {
        val token = SessionToken(context, ComponentName(context, AudioService::class.java))
        controllerFuture = MediaController.Builder(context, token).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                setupListener()
                syncInitialState()
                startReconciliationLoop()
            } catch (e: Exception) {
                Log.e("PlaybackClient", "Failed to connect to MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private val _playbackEnded = MutableSharedFlow<Unit>()
    val playbackEnded: SharedFlow<Unit> = _playbackEnded.asSharedFlow()

    private fun setupListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                processEvent(PlaybackEvent.IsPlayingChanged(isPlaying))
                if (!isPlaying) lastPauseTime = System.currentTimeMillis()
                if (isPlaying) startProgressPolling() else stopProgressPolling()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                processEvent(PlaybackEvent.IsBufferingChanged(playbackState == Player.STATE_BUFFERING))
                if (playbackState == Player.STATE_READY) {
                    controller?.duration?.takeIf { it > 0 }?.let { duration ->
                        processEvent(PlaybackEvent.MediaChanged(controller?.currentMediaItem?.mediaId, duration))
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    scope.launch { _playbackEnded.emit(Unit) }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                processEvent(PlaybackEvent.MediaChanged(
                    mediaId = mediaItem?.mediaId,
                    duration = controller?.duration?.coerceAtLeast(1) ?: 1
                ))
            }
        })
    }

    // Pure Reduction
    private fun reduce(currentState: PlaybackState, event: PlaybackEvent): PlaybackState {
        return when (event) {
            is PlaybackEvent.IsPlayingChanged -> currentState.copy(isPlaying = event.isPlaying)
            is PlaybackEvent.IsBufferingChanged -> currentState.copy(isBuffering = event.isBuffering)
            is PlaybackEvent.PositionUpdated -> currentState.copy(
                currentPosition = event.position,
                duration = if (event.duration > 0) event.duration else 1L,
                bufferedPosition = event.buffered
            )
            is PlaybackEvent.MediaChanged -> currentState.copy(
                currentMediaId = event.mediaId,
                duration = event.duration
            )
            is PlaybackEvent.SpeedChanged -> currentState.copy(playbackSpeed = event.speed)
        }
    }

    private fun processEvent(event: PlaybackEvent) {
        _state.update { reduce(it, event) }
    }

    private fun syncInitialState() {
        controller?.let { c ->
            processEvent(PlaybackEvent.IsPlayingChanged(c.isPlaying))
            processEvent(PlaybackEvent.IsBufferingChanged(c.playbackState == Player.STATE_BUFFERING))
            processEvent(PlaybackEvent.MediaChanged(c.currentMediaItem?.mediaId, c.duration.coerceAtLeast(1)))
            processEvent(PlaybackEvent.SpeedChanged(c.playbackParameters.speed))
            // Skip-silence applies service-side on player build + every READY (AudioSystem reads prefs).
            if (c.isPlaying) startProgressPolling()
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val c = controller ?: break
                processEvent(PlaybackEvent.PositionUpdated(c.currentPosition, c.duration, c.bufferedPosition))
                delay(POLL_MS)
            }
        }
    }

    private fun stopProgressPolling() {
        progressJob?.cancel()
        progressJob = null
    }

    // --- Declarative Driver (Reconciler) ---
    private fun startReconciliationLoop() {
        scope.launch {
            _desiredState.collect { desired ->
                controller?.let { c -> reconcile(desired, c) }
            }
        }
    }

    private fun reconcile(desired: DesiredState, c: MediaController) {
        if (desired.seekPosition != null) {
            c.seekTo(desired.seekPosition)
            _desiredState.update { it.copy(seekPosition = null) }
        }
        if (desired.isPlaying && !c.isPlaying) {
            c.play()
        } else if (!desired.isPlaying && c.isPlaying) {
            c.pause()
        }
        if (c.playbackParameters.speed != desired.playbackSpeed) {
            c.setPlaybackSpeed(desired.playbackSpeed)
        }
    }

    private fun mediaItemFor(podcast: PodcastEntity): MediaItem =
        MediaItem.Builder()
            .setMediaId(podcast.id)
            .setUri(podcast.audioUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(podcast.episodeTitle)
                    .setArtist(podcast.title)
                    .setArtworkUri(android.net.Uri.parse(podcast.imageUrl))
                    .build()
            )
            .build()

    /**
     * Play [podcast] as part of [queue]'s playlist (gapless advance, notification
     * next/prev). Restores saved position when resuming a started episode.
     */
    fun play(podcast: PodcastEntity, queue: List<PodcastEntity> = emptyList()) {
        val queueItems = queue.filter { it.audioUrl.isNotBlank() }
        val index = queueItems.indexOfFirst { it.id == podcast.id }
        val items: List<PodcastEntity>
        val startIndex: Int
        if (index >= 0) {
            items = queueItems
            startIndex = index
        } else {
            items = listOf(podcast)
            startIndex = 0
        }
        val startPosition = podcast.progress.takeIf { it > RESTORE_THRESHOLD_MS } ?: 0L
        val marker = mediaItemFor(podcast)
        controller?.let { c ->
            c.setMediaItems(items.map(::mediaItemFor), startIndex, startPosition)
            c.prepare()
        }
        _desiredState.update { it.copy(isPlaying = true, mediaItem = marker) }
    }

    fun enqueue(podcast: PodcastEntity) {
        if (podcast.audioUrl.isNotBlank()) controller?.addMediaItem(mediaItemFor(podcast))
    }

    fun enqueueNext(podcast: PodcastEntity) {
        val c = controller ?: return
        if (podcast.audioUrl.isNotBlank()) c.addMediaItem(c.currentMediaItemIndex + 1, mediaItemFor(podcast))
    }

    fun dequeue(id: String) {
        controller?.let { c ->
            (0 until c.mediaItemCount)
                .firstOrNull { c.getMediaItemAt(it).mediaId == id }
                ?.let { c.removeMediaItem(it) }
        }
    }

    fun playlistSize(): Int = controller?.mediaItemCount ?: 0

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }

    fun resume() {
        if (lastPauseTime > 0 && (System.currentTimeMillis() - lastPauseTime) > RESUME_REWIND_AFTER_MS) {
            val currentPos = controller?.currentPosition ?: 0L
            _desiredState.update { it.copy(seekPosition = (currentPos - RESUME_REWIND_MS).coerceAtLeast(0)) }
            Log.d("PlaybackClient", "Smart Resume: Rewind Intent Set")
        }
        lastPauseTime = 0
        _desiredState.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        _desiredState.update { it.copy(isPlaying = false) }
    }

    fun togglePlay() {
        if (_desiredState.value.isPlaying) pause() else resume()
    }

    fun seek(ms: Long) {
        _desiredState.update { it.copy(seekPosition = ms) }
    }

    fun skip(seconds: Int) {
        val current = controller?.currentPosition ?: 0L
        _desiredState.update { it.copy(seekPosition = current + (seconds * 1000)) }
    }

    fun setSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_SPEED, speed).apply()
        _desiredState.update { it.copy(playbackSpeed = speed) }
    }

    fun setSkipSilence(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()
        // Applied by AudioSystem at the next READY transition (controller cannot set it directly).
    }

    /** Takes effect on the next playback READY (enhancer attaches service-side). */
    fun setBoost(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BOOST, enabled).apply()
    }

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepAtEpisodeEnd.value = false
        initialSleepDuration = minutes * 60
        _sleepTimerSeconds.value = initialSleepDuration
        // Never force playback: a timer set while paused must stay paused.

        sleepTimerJob = scope.launch {
            while (_sleepTimerSeconds.value > 0) {
                delay(1000)
                _sleepTimerSeconds.value--
            }
            pause()
        }
    }

    /** Pause when the current episode ends. Countdown display mirrors remaining episode time. */
    fun startSleepTimerAtEnd() {
        sleepTimerJob?.cancel()
        _sleepAtEpisodeEnd.value = true
        val c = controller ?: return
        _sleepTimerSeconds.value = ((c.duration - c.currentPosition).coerceAtLeast(0) / 1000).toInt()
    }

    fun resetSleepTimer() {
        if (_sleepAtEpisodeEnd.value) return
        if (_sleepTimerSeconds.value > 0) {
            _sleepTimerSeconds.value = initialSleepDuration
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepAtEpisodeEnd.value = false
        initialSleepDuration = 0
        _sleepTimerSeconds.value = 0
    }

    private fun persistedSpeed(): Float =
        prefs.getFloat(KEY_SPEED, 1.0f).takeIf { it > 0f } ?: 1f

    fun cleanup() {
        stopProgressPolling()
        sleepTimerJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }

    private companion object {
        const val PREFS = "playback_prefs"
        const val KEY_SPEED = "speed"
        const val KEY_SKIP_SILENCE = "skip_silence"
        const val KEY_BOOST = "boost"
        const val POLL_MS = 500L
        const val RESTORE_THRESHOLD_MS = 10_000L
        const val RESUME_REWIND_AFTER_MS = 5 * 60 * 1000L
        const val RESUME_REWIND_MS = 3_000L
    }
}
