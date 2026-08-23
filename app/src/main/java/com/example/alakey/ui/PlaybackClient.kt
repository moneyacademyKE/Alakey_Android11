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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class PlaybackState(
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val duration: Long = 1L,
        val currentPosition: Long = 0L,
        val currentMediaId: String? = null, 
        val playbackSpeed: Float = 1.0f,
        val amplitude: Float = 0f
    )

    data class DesiredState(
        val isPlaying: Boolean = false,
        val mediaItem: MediaItem? = null,
        val seekPosition: Long? = null,
        val playbackSpeed: Float = 1.0f
    )

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()
    
    private val _desiredState = MutableStateFlow(DesiredState())

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var progressJob: Job? = null
    
    // Sleep Timer
    private val _sleepTimerSeconds = MutableStateFlow(0)
    val sleepTimerSeconds: StateFlow<Int> = _sleepTimerSeconds.asStateFlow()
    private var sleepTimerJob: Job? = null
    private var initialSleepDuration: Int = 0

    // Smart Resume
    private var lastPauseTime: Long = 0

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
                startReconciliationLoop() // Start the Driver
            } catch (e: Exception) {
                Log.e("PlaybackClient", "Failed to connect to MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private val _playbackEnded = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    val playbackEnded: kotlinx.coroutines.flow.SharedFlow<Unit> = _playbackEnded.asSharedFlow()

    private fun setupListener() {
        controller?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                processEvent(PlaybackEvent.IsPlayingChanged(isPlaying))
                if (isPlaying) startProgressPolling() else stopProgressPolling()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
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
                duration = if (event.duration > 0) event.duration else 1L, // Ensure non-zero
                amplitude = event.amplitude
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
            // Batch initial sync? Or emit sequence?
            // Let's just update directly or emit multiple events.
            // Direct reducers for distinct properties.
            processEvent(PlaybackEvent.IsPlayingChanged(c.isPlaying))
            processEvent(PlaybackEvent.MediaChanged(c.currentMediaItem?.mediaId, c.duration.coerceAtLeast(1)))
            processEvent(PlaybackEvent.SpeedChanged(c.playbackParameters.speed))
            
            if (c.isPlaying) startProgressPolling()
        }
    }

    private fun startProgressPolling() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var counter = 0f
            while (isActive) {
                val pos = controller?.currentPosition ?: 0L
                val dur = controller?.duration ?: 1L
                val amp = if (controller?.isPlaying == true) {
                    (Math.sin(counter.toDouble()).toFloat() * 0.2f + 0.8f) * (0.8f + Math.random().toFloat() * 0.4f)
                } else 0f
                processEvent(PlaybackEvent.PositionUpdated(pos, dur, amp))
                counter += 0.5f
                delay(100)
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
                controller?.let { c ->
                    reconcile(desired, c)
                }
            }
        }
    }

    private fun reconcile(desired: DesiredState, c: MediaController) {
        // 1. Reconcile Media ID
        if (desired.mediaItem != null && c.currentMediaItem?.mediaId != desired.mediaItem.mediaId) {
             c.setMediaItem(desired.mediaItem)
             c.prepare()
        }
        
        // 2. Reconcile Seek
        if (desired.seekPosition != null) {
            c.seekTo(desired.seekPosition)
            // Reset seek intent after consumption? Or treat as value? 
            // Ideally we consume it. For simplicity in this demo, strict value updates.
            _desiredState.update { it.copy(seekPosition = null) } 
        }

        // 3. Reconcile Play/Pause
        if (desired.isPlaying && !c.isPlaying) {
            c.play()
        } else if (!desired.isPlaying && c.isPlaying) {
            c.pause()
        }
        
        // 4. Reconcile Speed
        if (c.playbackParameters.speed != desired.playbackSpeed) {
            c.setPlaybackSpeed(desired.playbackSpeed)
        }
    }

    fun play(podcast: PodcastEntity) {
        val item = MediaItem.Builder()
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
            
        // Pure Intent Update
        _desiredState.update { 
            it.copy(isPlaying = true, mediaItem = item) 
        }
    }

    fun resume() {
        // Smart Resume Logic should happen at intent time or in Reconciler?
        // Intent time is simpler for now.
        if (lastPauseTime > 0 && (System.currentTimeMillis() - lastPauseTime) > 5 * 60 * 1000) {
            val currentPos = controller?.currentPosition ?: 0L
            val rewindPos = (currentPos - 3000).coerceAtLeast(0)
             _desiredState.update { it.copy(seekPosition = rewindPos) }
             Log.d("PlaybackClient", "Smart Resume: Rewind Intent Set")
        }
        lastPauseTime = 0
        _desiredState.update { it.copy(isPlaying = true) }
    }

    fun pause() {
        lastPauseTime = System.currentTimeMillis()
        _desiredState.update { it.copy(isPlaying = false) }
    }

    fun togglePlay() {
        // Toggle based on DESIRED state, not actual (debouncing)
        val currentlyDesired = _desiredState.value.isPlaying
        if (currentlyDesired) pause() else resume()
    }

    fun seek(ms: Long) {
        _desiredState.update { it.copy(seekPosition = ms) }
    }

    fun skip(seconds: Int) {
        val current = controller?.currentPosition ?: 0L
        _desiredState.update { it.copy(seekPosition = current + (seconds * 1000)) }
    }

    fun setSpeed(speed: Float) {
        _desiredState.update { it.copy(playbackSpeed = speed) }
    }
    
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        _sleepAtEpisodeEnd.value = false
        initialSleepDuration = minutes * 60
        _sleepTimerSeconds.value = initialSleepDuration
        resume()

        sleepTimerJob = scope.launch {
            while (_sleepTimerSeconds.value > 0) {
                delay(1000)
                _sleepTimerSeconds.value--
            }
            pause()
            // Reset volume if we were fading, but we're just pausing for now
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
    
    fun cleanup() {
        stopProgressPolling()
        sleepTimerJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
