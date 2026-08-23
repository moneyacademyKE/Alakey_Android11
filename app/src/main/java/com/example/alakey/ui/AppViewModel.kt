package com.example.alakey.ui

import android.content.Context
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.alakey.data.Chapter
import com.example.alakey.data.EventLogEntity
import com.example.alakey.data.ItunesSearchResult
import com.example.alakey.data.PodcastEntity
import com.example.alakey.data.UniversalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
        val isBuffering: Boolean = false,
        val currentTime: Long = 0,
        val duration: Long = 1,
        val bufferedMs: Long = 0,
        val speed: Float = 1f,
        val queue: List<PodcastEntity> = emptyList(),
        val inbox: List<PodcastEntity> = emptyList(),
        val chapters: List<Chapter> = emptyList(),
        val dominantColor: Int = AndroidColor.CYAN,
        val vibrantColor: Int = AndroidColor.CYAN,
        val mutedColor: Int = AndroidColor.GRAY,
        val sleepTimerSeconds: Int = 0,
        val marketOps: Map<String, AsyncOp> = emptyMap(),
        val downloadOps: Map<String, AsyncOp> = emptyMap()
    )

    sealed interface Action {
        data class Navigate(val screen: Screen) : Action
        data object Pop : Action
        data class SetPlayerOpen(val isOpen: Boolean) : Action
        data class Play(val podcast: PodcastEntity) : Action
        data object TogglePlay : Action
        data class Seek(val ms: Long) : Action
        data class Skip(val sec: Int) : Action
        data class SetSpeed(val speed: Float) : Action
        data class SetFilter(val filter: String) : Action
        data class SetCarMode(val enabled: Boolean) : Action
        data object PlayNextInQueue : Action
        data object PlayPreviousInQueue : Action
        data object CycleSleepTimer : Action
        data class Subscribe(
            val feedUrl: String,
            val title: String,
            val imageUrl: String,
            val marketQuery: String? = null
        ) : Action
        data class SetMarketOp(val query: String, val operation: AsyncOp) : Action
        data class SetDownloadOp(val episodeId: String, val operation: AsyncOp) : Action
        data class Rollback(val feedUrl: String, val marketQuery: String?, val error: String) : Action
    }

    sealed interface UserEvent {
        data class ShowMessage(val message: String) : UserEvent
        data class ShowError(val message: String) : UserEvent
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val _searchResults = MutableStateFlow<List<ItunesSearchResult>>(emptyList())
    val searchResults: StateFlow<List<ItunesSearchResult>> = _searchResults.asStateFlow()
    private val _logs = MutableStateFlow<List<EventLogEntity>>(emptyList())
    val logs: StateFlow<List<EventLogEntity>> = _logs.asStateFlow()
    private val _userEvents = MutableSharedFlow<UserEvent>()
    val userEvents: SharedFlow<UserEvent> = _userEvents.asSharedFlow()
    val sleepTimerSeconds: StateFlow<Int> = playbackClient.sleepTimerSeconds
    val sleepTimerTotalSeconds: StateFlow<Int> = playbackClient.sleepTimerTotalSeconds
    val sleepAtEpisodeEnd: StateFlow<Boolean> = playbackClient.sleepAtEpisodeEnd

    private val _history = androidx.compose.runtime.mutableStateListOf<UiState>()
    val history: List<UiState> get() = _history
    private var searchJob: Job? = null
    private val searchGeneration = AtomicLong(0)
    private var chaptersLoadedFor: String? = null
    private val chaptersCache = ConcurrentHashMap<String, List<Chapter>>()

    init {
        viewModelScope.launch { playbackClient.playbackEnded.collect { playNextEpisode() } }
        viewModelScope.launch {
            repo.library.collect { library ->
                updateState { state ->
                    val queue = library.filter { it.isInQueue }.sortedBy { it.queueOrder }
                    val inbox = library.filter { !it.isInQueue && it.progress == 0L }
                    val current = state.current?.let { old -> library.find { it.id == old.id } ?: old }
                    state.copy(podcasts = library, queue = queue, inbox = inbox, current = current)
                }
            }
        }
        viewModelScope.launch {
            var lastSaveMs = 0L
            var wasPlaying = false
            playbackClient.state.collect { playback ->
                val known = _uiState.value.podcasts + _uiState.value.optimisticPodcasts
                val podcast = known.find { it.id == playback.currentMediaId }
                updateState { state ->
                    state.copy(
                        current = podcast ?: state.current,
                        isPlaying = playback.isPlaying,
                        isBuffering = playback.isBuffering,
                        currentTime = playback.currentPosition,
                        duration = playback.duration,
                        bufferedMs = playback.bufferedPosition,
                        speed = playback.playbackSpeed
                    )
                }
                if (podcast != null && podcast.imageUrl != _uiState.value.current?.imageUrl) extractColor(podcast.imageUrl)
                if (podcast != null && playback.currentMediaId != chaptersLoadedFor) loadChapters(podcast)
                val now = System.currentTimeMillis()
                val playingSave = playback.isPlaying && now - lastSaveMs >= PROGRESS_SAVE_INTERVAL_MS
                val pauseSave = wasPlaying && !playback.isPlaying && now - lastSaveMs >= PROGRESS_SAVE_MIN_GAP_MS
                if (podcast != null && playback.currentPosition > 0 && (playingSave || pauseSave)) {
                    lastSaveMs = now
                    repo.updateProgress(podcast.id, playback.currentPosition)
                    repo.updateLastPlayed(podcast.id, now)
                }
                wasPlaying = playback.isPlaying
            }
        }
        viewModelScope.launch {
            playbackClient.sleepTimerSeconds.collect { seconds -> updateState { it.copy(sleepTimerSeconds = seconds) } }
        }
    }

    fun dispatch(action: Action) {
        updateState { AppReducer.reduce(it, action) }
        if (action is Action.Rollback) emitEvent(UserEvent.ShowError(action.error))
        handleEffects(action)
    }

    private fun handleEffects(action: Action) {
        when (action) {
            is Action.Play -> playbackClient.play(action.podcast, _uiState.value.queue)
            Action.TogglePlay -> playbackClient.togglePlay()
            is Action.Seek -> playbackClient.seek(action.ms)
            is Action.Skip -> playbackClient.skip(action.sec)
            is Action.SetSpeed -> playbackClient.setSpeed(action.speed)
            is Action.Subscribe -> viewModelScope.launch {
                repo.subscribe(action.feedUrl)
                    .onSuccess {
                        updateState { it.copy(optimisticPodcasts = it.optimisticPodcasts.filterNot { p -> p.feedUrl == action.feedUrl }) }
                        action.marketQuery?.let { dispatch(Action.SetMarketOp(it, AsyncOp.Done)) }
                        emitEvent(UserEvent.ShowMessage("Subscribed"))
                    }
                    .onFailure { error ->
                        dispatch(Action.Rollback(action.feedUrl, action.marketQuery, error.message ?: "Subscription failed"))
                    }
            }
            Action.PlayNextInQueue -> playNextQueued()
            Action.PlayPreviousInQueue -> playPreviousQueued()
            Action.CycleSleepTimer -> cycleTimer()
            else -> Unit
        }
    }

    private fun playNextQueued() {
        if (playbackClient.playlistSize() > 1) { playbackClient.next(); return }
        val queue = _uiState.value.queue
        val index = queue.indexOfFirst { it.id == _uiState.value.current?.id }
        if (index >= 0 && index + 1 < queue.size) playbackClient.play(queue[index + 1])
        else emitEvent(UserEvent.ShowMessage("End of queue"))
    }

    private fun playPreviousQueued() {
        if (playbackClient.state.value.currentPosition > 5_000) {
            playbackClient.seek(0)
            return
        }
        if (playbackClient.playlistSize() > 1) { playbackClient.previous(); return }
        val queue = _uiState.value.queue
        val index = queue.indexOfFirst { it.id == _uiState.value.current?.id }
        if (index > 0) playbackClient.play(queue[index - 1]) else playbackClient.seek(0)
    }

    private fun cycleTimer() {
        val current = playbackClient.sleepTimerSeconds.value
        val minutes = when {
            playbackClient.sleepAtEpisodeEnd.value -> null // OFF
            current == 0 -> 15
            current <= 15 * 60 -> 30
            current <= 30 * 60 -> 45
            current <= 45 * 60 -> 60
            else -> -1 // end of episode
        }
        when (minutes) {
            null -> playbackClient.cancelSleepTimer()
            -1 -> playbackClient.startSleepTimerAtEnd()
            0 -> playbackClient.cancelSleepTimer()
            else -> playbackClient.startSleepTimer(minutes)
        }
    }

    private fun loadChapters(podcast: PodcastEntity) {
        chaptersLoadedFor = podcast.id
        val url = podcast.attributes["chaptersUrl"]
        if (url.isNullOrBlank()) {
            updateState { it.copy(chapters = emptyList()) }
            return
        }
        viewModelScope.launch {
            val chapters = chaptersCache.getOrPut(url) { repo.fetchChapters(url) }
            if (_uiState.value.current?.id == podcast.id) updateState { it.copy(chapters = chapters) }
        }
    }

    private fun updateState(transform: (UiState) -> UiState) {
        _uiState.update { current ->
            val next = transform(current)
            if (next != current) {
                _history.add(next)
                if (_history.size > 50) _history.removeAt(0)
            }
            next
        }
    }

    private fun emitEvent(event: UserEvent) { viewModelScope.launch { _userEvents.emit(event) } }
    fun connect() = Unit
    fun startSleepTimer(minutes: Int = 45) = playbackClient.startSleepTimer(minutes)
    fun startSleepTimerAtEnd() = playbackClient.startSleepTimerAtEnd()
    fun resetSleepTimer() = playbackClient.resetSleepTimer()
    fun cancelSleepTimer() = playbackClient.cancelSleepTimer()

    fun downloadEpisode(episodeId: String) {
        dispatch(Action.SetDownloadOp(episodeId, AsyncOp.InFlight))
        viewModelScope.launch {
            repo.downloadAudio(episodeId)
                .onSuccess { dispatch(Action.SetDownloadOp(episodeId, AsyncOp.Done)) }
                .onFailure { error ->
                    Log.e("AppViewModel", "Download failure for $episodeId", error)
                    dispatch(Action.SetDownloadOp(episodeId, AsyncOp.Failed(error.message ?: "Download failed")))
                }
        }
    }

    fun checkForAutoDownloads() { viewModelScope.launch { repo.runSmartDownloads() } }
    fun importFeed(url: String) = dispatch(Action.Subscribe(url, "RSS Feed", ""))

    fun searchPodcasts(query: String) {
        searchJob?.cancel()
        val normalized = query.trim()
        if (normalized.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val generation = searchGeneration.incrementAndGet()
            repo.searchPodcasts(normalized)
                .onSuccess { if (generation == searchGeneration.get()) _searchResults.value = it }
                .onFailure { error ->
                    if (generation == searchGeneration.get()) emitEvent(UserEvent.ShowError("Search failed: ${error.message}"))
                }
        }
    }

    fun marketplaceSubscribe(query: String) {
        dispatch(Action.SetMarketOp(query, AsyncOp.InFlight))
        viewModelScope.launch {
            repo.searchPodcasts(query)
                .onSuccess { results ->
                    val result = results.firstOrNull()
                    if (result == null) dispatch(Action.SetMarketOp(query, AsyncOp.Failed("No results found")))
                    else dispatch(Action.Subscribe(result.feedUrl, result.collectionName, result.artworkUrl100, query))
                }
                .onFailure { error -> dispatch(Action.SetMarketOp(query, AsyncOp.Failed(error.message ?: "Search failed"))) }
        }
    }

    fun play(podcast: PodcastEntity) = dispatch(Action.Play(podcast))
    fun togglePlay() = dispatch(Action.TogglePlay)
    fun seek(ms: Long) = dispatch(Action.Seek(ms.coerceAtLeast(0)))
    fun skip(seconds: Int) = dispatch(Action.Skip(seconds))
    fun setPlaybackSpeed(speed: Float) = dispatch(Action.SetSpeed(speed))
    fun setSkipSilence(enabled: Boolean) = playbackClient.setSkipSilence(enabled)
    fun setVolumeBoost(enabled: Boolean) = playbackClient.setBoost(enabled)
    fun unsubscribe(title: String) { viewModelScope.launch { repo.unsubscribe(title) } }
    fun addToQueue(podcast: PodcastEntity) {
        viewModelScope.launch { repo.addToQueue(podcast.id) }
        playbackClient.enqueue(podcast)
    }
    fun removeFromQueue(podcast: PodcastEntity) {
        viewModelScope.launch { repo.removeFromQueue(podcast.id) }
        playbackClient.dequeue(podcast.id)
    }
    fun resumeLastPlayed() { viewModelScope.launch { repo.getLastPlayedPodcast()?.let(::play) } }
    fun resumePlayback() = playbackClient.resume()
    fun playRadio() {
        viewModelScope.launch {
            val candidate = repo.getRadioCandidate()
            if (candidate == null) emitEvent(UserEvent.ShowError("No unplayed episodes found"))
            else { repo.addToQueue(candidate.id); play(candidate) }
        }
    }
    fun markPlayed(podcast: PodcastEntity) { viewModelScope.launch { repo.markPlayed(podcast) } }
    fun markOlderPlayed(podcast: PodcastEntity) { viewModelScope.launch { repo.markOlderAsPlayed(podcast) } }
    fun deleteDownload(podcast: PodcastEntity) {
        dispatch(Action.SetDownloadOp(podcast.id, AsyncOp.InFlight))
        viewModelScope.launch {
            runCatching { repo.deleteDownload(podcast.id) }
                .onSuccess { dispatch(Action.SetDownloadOp(podcast.id, AsyncOp.Done)) }
                .onFailure { dispatch(Action.SetDownloadOp(podcast.id, AsyncOp.Failed(it.message ?: "Delete failed"))) }
        }
    }
    fun playNext(podcast: PodcastEntity) {
        viewModelScope.launch { repo.addToQueueNext(podcast.id) }
        playbackClient.enqueueNext(podcast)
    }
    fun navigate(screen: Screen) = dispatch(Action.Navigate(screen))
    fun setPlayerOpen(open: Boolean) = dispatch(Action.SetPlayerOpen(open))
    fun setCarMode(enabled: Boolean) = dispatch(Action.SetCarMode(enabled))
    fun setFilter(filter: String) = dispatch(Action.SetFilter(filter))
    fun refreshLogs() { viewModelScope.launch { _logs.value = repo.getRecentLogs() } }
    fun playNextEpisode() = dispatch(Action.PlayNextInQueue)
    fun playPreviousEpisode() = dispatch(Action.PlayPreviousInQueue)
    fun cycleSleepTimer() = dispatch(Action.CycleSleepTimer)

    private fun extractColor(url: String) {
        viewModelScope.launch {
            val podcast = _uiState.value.current
            val palette = podcast?.palette ?: paletteExtractor.extract(url)
            if (palette != null) {
                updateState { it.copy(dominantColor = palette.dominant, vibrantColor = palette.vibrant, mutedColor = palette.muted) }
                if (podcast != null && podcast.palette == null) repo.savePalette(podcast.id, palette)
            }
        }
    }

    override fun onCleared() {
        playbackClient.cleanup()
        super.onCleared()
    }

    private companion object {
        const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
        const val PROGRESS_SAVE_MIN_GAP_MS = 2_000L
    }
}
