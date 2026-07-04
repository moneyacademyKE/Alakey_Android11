package com.example.alakey.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alakey.data.ItunesSearchResult
import com.example.alakey.data.PodcastEntity
import com.example.alakey.data.UniversalRepository
import com.example.alakey.data.EventLogEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor

@HiltViewModel
class AppViewModel @Inject constructor(
    private val repo: UniversalRepository,
    private val playbackClient: PlaybackClient,
    private val paletteExtractor: PaletteExtractor,
    @ApplicationContext private val context: Context
) : ViewModel() {
    
    enum class Screen { Library, Marketplace, Inbox, Queue }

    data class UiState(
        val navigationStack: List<Screen> = listOf(Screen.Library),
        val isPlayerOpen: Boolean = false,
        val isCarMode: Boolean = false,
        val activeFilter: String = "All",
        
        val podcasts: List<PodcastEntity> = emptyList(),
        val optimisticPodcasts: List<PodcastEntity> = emptyList(),
        val current: PodcastEntity? = null,
        val isPlaying: Boolean = false,
        val currentTime: Long = 0,
        val duration: Long = 1,
        val speed: Float = 1.0f,
        val queue: List<PodcastEntity> = emptyList(),
        val inbox: List<PodcastEntity> = emptyList(),
        val amplitude: Float = 0f,
        val dominantColor: Int = AndroidColor.CYAN,
        val vibrantColor: Int = AndroidColor.CYAN,
        val mutedColor: Int = AndroidColor.GRAY,
        val sleepTimerSeconds: Int = 0
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // Epochal Time Travel (History Tape)
    private val _history = androidx.compose.runtime.mutableStateListOf<UiState>()
    val history: List<UiState> get() = _history
    
    private fun updateState(function: (UiState) -> UiState) {
        _uiState.update { current ->
            val newState = function(current)
            if (newState != current) {
                _history.add(newState)
                // Keep history finite (simplicity constraint)
                if (_history.size > 50) _history.removeAt(0)
            }
            newState
        }
    }
    
    fun travelTo(index: Int) {
         if (index in _history.indices) {
             _uiState.value = _history[index] // Set value directly (no new history record)
         }
    }

    private val _searchResults = MutableStateFlow<List<ItunesSearchResult>>(emptyList())
    val searchResults: StateFlow<List<ItunesSearchResult>> = _searchResults.asStateFlow()
    
    // Observability
    private val _logs = MutableStateFlow<List<EventLogEntity>>(emptyList())
    val logs: StateFlow<List<EventLogEntity>> = _logs.asStateFlow()

    val sleepTimerSeconds: StateFlow<Int> = playbackClient.sleepTimerSeconds

    sealed interface UserEvent {
        data class ShowMessage(val message: String) : UserEvent
        data class ShowError(val message: String) : UserEvent
    }
    
    private val _userEvents = MutableSharedFlow<UserEvent>()
    val userEvents: SharedFlow<UserEvent> = _userEvents.asSharedFlow()

    // --- Phase 6: Logical Frontend (Interceptor Chain) ---
    sealed interface Action {
        data class Navigate(val screen: Screen) : Action
        object Pop : Action
        data class SetPlayerOpen(val isOpen: Boolean) : Action
        data class Play(val podcast: PodcastEntity) : Action
        object TogglePlay : Action
        data class Seek(val ms: Long) : Action
        data class Skip(val sec: Int) : Action
        data class SetSpeed(val speed: Float) : Action
        data class SetFilter(val filter: String) : Action
        data class SetCarMode(val enabled: Boolean) : Action
        object PlayNextInQueue : Action
        object PlayPreviousInQueue : Action
        object CycleSleepTimer : Action
        
        // Optimistic Actions
        data class Subscribe(val feedUrl: String, val title: String, val imageUrl: String) : Action
        data class Rollback(val historyIndex: Int, val error: String) : Action
    }
    
    // Interceptor: (Action, State) -> Action? or Effect?
    // Simplified: dispatch handles side effects vs pure state updates.
    fun dispatch(action: Action) {
        logAction(action) // Interceptor 1: Log
        
        updateState { AppReducer.reduce(it, action) }

        when(action) {
            is Action.Rollback -> {
                travelTo(action.historyIndex)
                emitEvent(UserEvent.ShowError("Rollback: ${action.error}"))
            }
            else -> {}
        }

        // Interceptor 3: Effects (Side Effects)
        handleEffects(action)
        
        // Post-Action Observability: Refresh logs if action might have logged something
        // Just eager refresh for now (Optimization later)
        refreshLogs()
    }
    
    private fun logAction(action: Action) {
        if (action !is Action.Seek) { // Reduce noise
             Log.d("Dispatcher", "Action: $action")
        }
    }
    
    private fun handleEffects(action: Action) {
        when(action) {
            is Action.Play -> playbackClient.play(action.podcast)
            is Action.TogglePlay -> playbackClient.togglePlay()
            is Action.Seek -> playbackClient.seek(action.ms)
            is Action.Skip -> playbackClient.skip(action.sec)
            is Action.SetSpeed -> playbackClient.setSpeed(action.speed)
            is Action.Subscribe -> {
                val previousIndex = _history.size - 2
                viewModelScope.launch {
                    repo.subscribe(action.feedUrl)
                        .onSuccess {
                            updateState { s -> s.copy(optimisticPodcasts = s.optimisticPodcasts.filter { it.feedUrl != action.feedUrl }) }
                            emitEvent(UserEvent.ShowMessage("Subscribed!"))
                        }
                        .onFailure {
                            dispatch(Action.Rollback(previousIndex, it.message ?: "Network error"))
                        }
                }
            }
            is Action.PlayNextInQueue -> {
                val queue = _uiState.value.queue
                val current = _uiState.value.current
                if (queue.isNotEmpty()) {
                    val idx = queue.indexOfFirst { it.id == current?.id }
                    if (idx != -1 && idx < queue.size - 1) {
                         playbackClient.play(queue[idx + 1])
                    } else {
                         emitEvent(UserEvent.ShowMessage("End of queue"))
                    }
                }
            }
            is Action.PlayPreviousInQueue -> {
                 // Classic Logic: If > 5s, restart. Else prev.
                 val pos = playbackClient.state.value.currentPosition
                 if (pos > 5000) {
                     playbackClient.seek(0)
                 } else {
                     val queue = _uiState.value.queue
                     val current = _uiState.value.current
                     if (queue.isNotEmpty()) {
                         val idx = queue.indexOfFirst { it.id == current?.id }
                         if (idx > 0) {
                             playbackClient.play(queue[idx - 1])
                         } else {
                             playbackClient.seek(0)
                         }
                     }
                 }
            }
            is Action.CycleSleepTimer -> {
                val current = playbackClient.sleepTimerSeconds.value
                val newDuration = when {
                    current == 0 -> 15
                    current <= 15 * 60 -> 30
                    current <= 30 * 60 -> 45
                    current <= 45 * 60 -> 60
                    else -> 0
                }
                if (newDuration > 0) {
                    playbackClient.startSleepTimer(newDuration)
                    emitEvent(UserEvent.ShowMessage("Sleep Timer: ${newDuration}m"))
                } else {
                    playbackClient.resetSleepTimer() // actually this resets to initial, we want cancel.
                    // Let's assume startSleepTimer(0) cancels or we need a cancel. 
                    // Reuse startSleepTimer logic but need to ensure it handles 0 or cancel.
                    // Checking PlaybackClient.. startSleepTimer loop condition is > 0.
                    // So we can just set it to 0. 
                    // But PlaybackClient doesn't expose a "Cancel" directly other than cleanup.
                    // Let's stick to startSleepTimer logic... wait, I need to check PlaybackClient again.
                    // It has resetSleepTimer which resets to *initial*. 
                    // I will implement a cancel logic by just calling startSleepTimer with 0 or a new method.
                    // For now, let's assume setting it to 0 via startSleepTimer or a new method is needed.
                    // I'll use startSleepTimer(0) and rely on the loop condition, hoping it handles it.
                    // Looking at PlaybackClient again: Loop `while (_sleepTimerSeconds.value > 0)`.
                    // So setting value to 0 will break loop.
                    // But `startSleepTimer` sets `_sleepTimerSeconds.value = initialSleepDuration`.
                    // So `startSleepTimer(0)` sets it to 0 and loop won't start (if check is before).
                    // Correct.
                    playbackClient.startSleepTimer(0)
                    emitEvent(UserEvent.ShowMessage("Sleep Timer Off"))
                }
            }
            else -> {}
        }
    }

    init {
        // Playback Continuity
        viewModelScope.launch {
            playbackClient.playbackEnded.collect {
                playNextEpisode()
            }
        }

        // Epochal Reconciler: repository exposes fact-hydrated values.
        viewModelScope.launch {
            repo.library.collect { hydratedLibrary ->
                val hydratedQueue = hydratedLibrary.filter { it.isInQueue }.sortedBy { it.queueOrder }
                val hydratedInbox = hydratedLibrary.filter { !it.isInQueue && it.progress == 0L }

                updateState { s ->
                    val currentId = s.current?.id
                    val hydratedCurrent = if (currentId != null) {
                        hydratedLibrary.find { it.id == currentId } ?: s.current
                    } else s.current

                    s.copy(
                        podcasts = hydratedLibrary,
                        queue = hydratedQueue,
                        inbox = hydratedInbox,
                        current = hydratedCurrent
                    )
                }
            }
        }

        // Hydrate Playback State (Independent of Application Facts for now, as it's real-time hardware state)
        viewModelScope.launch {
            playbackClient.state.collect { pbState ->
                val allKnown = _uiState.value.podcasts + _uiState.value.optimisticPodcasts
                val podcast = allKnown.find { it.id == pbState.currentMediaId }
                
                updateState {
                    it.copy(
                        current = podcast ?: it.current,
                        isPlaying = pbState.isPlaying,
                        currentTime = pbState.currentPosition,
                        duration = pbState.duration,
                        speed = pbState.playbackSpeed,
                        amplitude = pbState.amplitude
                    )
                }
                
                if (podcast != null && podcast.imageUrl != _uiState.value.current?.imageUrl) {
                    extractColor(podcast.imageUrl)
                }
                
                if (pbState.isPlaying && podcast != null && pbState.currentPosition > 0) {
                     repo.updateProgress(podcast.id, pbState.currentPosition)
                     repo.updateLastPlayed(podcast.id, System.currentTimeMillis())
                }
            }
        }
        
        // Sleep Timer Sync
        viewModelScope.launch {
            playbackClient.sleepTimerSeconds.collect { seconds ->
                updateState { it.copy(sleepTimerSeconds = seconds) }
            }
        }
    }

    private fun emitEvent(event: UserEvent) {
        viewModelScope.launch { _userEvents.emit(event) }
    }

    fun connect() {
        // No-op: Client connects on init. 
    }

    fun startSleepTimer(minutes: Int = 45) {
        playbackClient.startSleepTimer(minutes)
    }

    fun resetSleepTimer() {
        playbackClient.resetSleepTimer()
    }

    fun cancelSleepTimer() {
        playbackClient.cancelSleepTimer()
    }

    fun downloadEpisode(podcastId: String) {
        viewModelScope.launch {
            repo.downloadAudio(podcastId)
                .onFailure { e -> Log.e("AppViewModel", "Download failure for $podcastId", e) }
        }
    }

    fun checkForAutoDownloads() {
        viewModelScope.launch {
            repo.runSmartDownloads()
        }
    }

    override fun onCleared() {
        super.onCleared()
        playbackClient.cleanup()
    }

    fun importFeed(url: String) {
        dispatch(Action.Subscribe(url, "RSS Feed", ""))
    }

    fun searchPodcasts(query: String) {
        viewModelScope.launch {
            repo.searchPodcasts(query)
                .onSuccess { _searchResults.value = it }
                .onFailure { emitEvent(UserEvent.ShowError("Search failed: ${it.message}")) }
        }
    }

    fun play(p: PodcastEntity) {
        dispatch(Action.Play(p))
    }

    fun togglePlay() {
        dispatch(Action.TogglePlay)
    }

    fun seek(ms: Long) {
        dispatch(Action.Seek(ms))
    }

    fun skip(sec: Int) {
        dispatch(Action.Skip(sec))
    }

    fun setPlaybackSpeed(speed: Float) {
        dispatch(Action.SetSpeed(speed))
    }

    fun unsubscribe(title: String) {
        viewModelScope.launch {
            repo.unsubscribe(title)
        }
    }

    fun marketplaceSubscribe(query: String) {
        viewModelScope.launch {
            repo.searchPodcasts(query).onSuccess { results ->
                if (results.isNotEmpty()) {
                    val r = results.first()
                    dispatch(Action.Subscribe(r.feedUrl, r.collectionName, r.artworkUrl100))
                } else {
                    emitEvent(UserEvent.ShowMessage("No results found for $query"))
                }
            }.onFailure { emitEvent(UserEvent.ShowError("Search failed")) }
        }
    }

    fun addToQueue(podcast: PodcastEntity) {
        viewModelScope.launch {
            repo.addToQueue(podcast.id)
        }
    }

    fun removeFromQueue(podcast: PodcastEntity) {
        viewModelScope.launch {
            repo.removeFromQueue(podcast.id)
        }
    }

    fun resumeLastPlayed() {
        viewModelScope.launch {
            repo.getLastPlayedPodcast()?.let { play(it) }
        }
    }
    
    fun resumePlayback() {
        playbackClient.resume()
    }

    private fun extractColor(url: String) {
        viewModelScope.launch {
            val currentPodcast = _uiState.value.current
            
            // 1. Data-First: Check if we already have the fact stored
            if (currentPodcast != null && currentPodcast.palette != null && currentPodcast.imageUrl == url) {
                updateState { it.copy(
                    dominantColor = currentPodcast.palette.dominant,
                    vibrantColor = currentPodcast.palette.vibrant,
                    mutedColor = currentPodcast.palette.muted
                ) }
                return@launch
            }

            val palette = paletteExtractor.extract(url)
            if (palette != null) {
                updateState { it.copy(
                    dominantColor = palette.dominant,
                    vibrantColor = palette.vibrant,
                    mutedColor = palette.muted
                ) }
                if (currentPodcast != null) repo.savePalette(currentPodcast.id, palette)
            } else {
                Log.w("AppViewModel", "Color extraction failed for $url")
            }
        }
    }

    fun playRadio() {
        viewModelScope.launch {
            val candidate = repo.getRadioCandidate()
            if (candidate != null) {
                repo.addToQueue(candidate.id)
                play(candidate)
                emitEvent(UserEvent.ShowMessage("Radio: Now playing ${candidate.episodeTitle}"))
            } else {
                emitEvent(UserEvent.ShowError("Radio silence. No unplayed episodes found."))
            }
        }
    }
    
    fun markPlayed(p: PodcastEntity) {
        viewModelScope.launch {
            repo.markPlayed(p)
            emitEvent(UserEvent.ShowMessage("Marked as played"))
        }
    }

    fun markOlderPlayed(p: PodcastEntity) {
        viewModelScope.launch {
            repo.markOlderAsPlayed(p)
            emitEvent(UserEvent.ShowMessage("Archived older episodes"))
        }
    }

    fun deleteDownload(p: PodcastEntity) {
        viewModelScope.launch {
            repo.deleteDownload(p.id)
            emitEvent(UserEvent.ShowMessage("Download deleted"))
        }
    }
    
    fun playNext(p: PodcastEntity) {
        viewModelScope.launch {
            repo.addToQueue(p.id) // Currently adds to end. 
            // TODO: Implement "Add to Top" in Dao if strict "Play Next" needed.
            // For now, "Add to Queue" is sufficient context.
            emitEvent(UserEvent.ShowMessage("Added to queue"))
        }
    }
    
    // --- Navigation Logic (Pure Value) ---
    fun navigate(screen: Screen) {
        dispatch(Action.Navigate(screen))
    }
    
    fun setPlayerOpen(isOpen: Boolean) {
        dispatch(Action.SetPlayerOpen(isOpen))
    }
    
    fun setCarMode(isCarMode: Boolean) {
        dispatch(Action.SetCarMode(isCarMode))
    }
    
    fun setFilter(filter: String) {
        dispatch(Action.SetFilter(filter))
    }
    
    fun refreshLogs() {
        viewModelScope.launch {
            _logs.value = repo.getRecentLogs()
        }
    }

    fun playNextEpisode() {
        dispatch(Action.PlayNextInQueue)
    }

    fun playPreviousEpisode() {
        dispatch(Action.PlayPreviousInQueue)
    }

    fun cycleSleepTimer() {
        dispatch(Action.CycleSleepTimer)
    }
}
