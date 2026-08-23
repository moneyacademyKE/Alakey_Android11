package com.example.alakey.ui

import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.alakey.data.ItunesSearchResult
import com.example.alakey.data.PodcastEntity
import com.example.alakey.domain.HeadsetResumeLogic

@Composable
fun MainContent() {
    val vm: AppViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val sleepTimerSeconds by vm.sleepTimerSeconds.collectAsState()
    val sleepTimerTotalSeconds by vm.sleepTimerTotalSeconds.collectAsState()
    val sleepAtEnd by vm.sleepAtEpisodeEnd.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val motionDetector = remember {
        MotionDetector {
            vm.resetSleepTimer()
            Toast.makeText(context, "Sleep timer extended", Toast.LENGTH_SHORT).show()
        }
    }
    var notificationPrompted by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(sleepTimerSeconds > 0) {
        if (sleepTimerSeconds > 0) {
            sensorManager.registerListener(motionDetector, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sensorManager.unregisterListener(motionDetector) }
    }

    LaunchedEffect(Unit) {
        vm.connect()
        vm.checkForAutoDownloads()
        vm.userEvents.collect { event ->
            val message = when (event) {
                is AppViewModel.UserEvent.ShowMessage -> event.message
                is AppViewModel.UserEvent.ShowError -> "Error: ${event.message}"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(state.isPlaying) {
        if (!notificationPrompted && state.isPlaying && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPrompted = true
            notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    HeadsetResumeEffect(vm)
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showSleepSheet by rememberSaveable { mutableStateOf(false) }
    val activeScreen = state.navigationStack.lastOrNull() ?: AppViewModel.Screen.Library

    BackHandler(enabled = state.isCarMode || state.navigationStack.size > 1 || state.isPlayerOpen) {
        when {
            state.isCarMode -> vm.setCarMode(false)
            state.isPlayerOpen -> vm.setPlayerOpen(false)
            else -> vm.dispatch(AppViewModel.Action.Pop)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        FluxBackground(color = Color(state.dominantColor))
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Header(activeScreen.name, vm::playRadio, { vm.setCarMode(true) }, { showAddDialog = true })
            AnimatedContent(
                targetState = activeScreen,
                transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = .95f)).togetherWith(fadeOut(tween(200))) },
                label = "screen_transition",
                modifier = Modifier.weight(1f)
            ) { screen ->
                when (screen) {
                    AppViewModel.Screen.Library -> LibraryContent(state, vm, { showAddDialog = true }, { showSleepSheet = true })
                    AppViewModel.Screen.Inbox -> EpisodeList(state.inbox, state, vm)
                    AppViewModel.Screen.Queue -> EpisodeList(state.queue, state, vm, dismissible = true)
                    AppViewModel.Screen.Marketplace -> GlassMarketplace(state.marketOps, vm::marketplaceSubscribe)
                }
            }
        }
        if (state.current != null && !state.isCarMode) {
            PlayerHost(
                spec = state.toPlayerSpec(sleepTimerSeconds, sleepTimerTotalSeconds), expanded = state.isPlayerOpen, queueCount = state.queue.size,
                onOpen = { vm.setPlayerOpen(true) }, onClose = { vm.setPlayerOpen(false) }, onTogglePlay = vm::togglePlay,
                onQueue = { vm.navigate(AppViewModel.Screen.Queue) }, onSeek = vm::seek, onSkip = vm::skip,
                onSetSpeed = vm::setPlaybackSpeed, onNext = vm::playNextEpisode, onPrevious = vm::playPreviousEpisode,
                onSleepTimer = { showSleepSheet = true }, modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        if (!state.isPlayerOpen && !state.isCarMode) {
            GlassDock(activeScreen, vm::navigate, Modifier.align(Alignment.BottomCenter))
        }
        if (state.isCarMode) {
            CarModeScreen(
                state.current?.let { state.toPlayerSpec(sleepTimerSeconds) }, vm::togglePlay,
                { vm.skip(PlayerTokens.SKIP_FORWARD_SECONDS) }, { vm.skip(-PlayerTokens.SKIP_BACK_SECONDS) },
                { vm.setCarMode(false) }
            )
        }
    }

    if (showAddDialog) {
        AddPodcastDialog({ showAddDialog = false }, { vm.importFeed(it); showAddDialog = false }, vm::searchPodcasts, searchResults)
    }
    if (showSleepSheet) {
        SleepTimerSheet(sleepTimerSeconds, sleepAtEnd, { showSleepSheet = false }) { minutes ->
            when (minutes) {
                -1 -> vm.startSleepTimerAtEnd()
                0 -> vm.cancelSleepTimer()
                else -> vm.startSleepTimer(minutes)
            }
            showSleepSheet = false
        }
    }
}

@Composable
private fun HeadsetResumeEffect(vm: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var previous by remember { mutableStateOf<Boolean?>(null) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val connected = when (intent.action) {
                    Intent.ACTION_HEADSET_PLUG -> intent.getIntExtra("state", -1).let { if (it < 0) null else it == 1 }
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1).let { if (it < 0) null else it == BluetoothProfile.STATE_CONNECTED }
                    else -> null
                }
                if (connected != null) {
                    if (HeadsetResumeLogic.shouldResume(previous, connected)) vm.resumePlayback()
                    previous = connected
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        val flags = if (Build.VERSION.SDK_INT >= 33) ContextCompat.RECEIVER_NOT_EXPORTED else 0
        ContextCompat.registerReceiver(context, receiver, filter, flags)
        onDispose { context.unregisterReceiver(receiver) }
    }
}

private fun AppViewModel.UiState.toPlayerSpec(timer: Int, timerTotal: Int = 0) = PlayerSpec(
    title = current?.episodeTitle.orEmpty(),
    artist = current?.title.orEmpty(),
    imageUrl = current?.imageUrl.orEmpty(),
    isPlaying = isPlaying,
    isBuffering = isBuffering,
    currentMs = currentTime,
    durationMs = duration,
    bufferedMs = bufferedMs,
    speed = speed,
    sleepTimerSeconds = timer,
    sleepTimerTotalSeconds = timerTotal,
    chapters = chapters,
    currentChapterIndex = if (chapters.isEmpty()) -1 else chapters.indexOfLast { it.start <= currentTime },
    dominantColor = dominantColor,
    vibrantColor = vibrantColor,
    mutedColor = mutedColor
)

@Composable
private fun Header(title: String, onRadio: () -> Unit, onCarMode: () -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Text(title, color = Color.White, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Row {
            HeaderAction(Icons.Rounded.Radio, "Play radio", onRadio)
            HeaderAction(Icons.Rounded.DirectionsCar, "Open car mode", onCarMode)
            HeaderAction(Icons.Rounded.Add, "Add podcast", onAdd)
        }
    }
}

@Composable
private fun HeaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    IconButton(onClick, Modifier.size(48.dp).pressScale(interaction).semantics { role = Role.Button }, interactionSource = interaction) { Icon(icon, label, tint = Color.White) }
}

@Composable
private fun LibraryContent(state: AppViewModel.UiState, vm: AppViewModel, onAdd: () -> Unit, onTimer: () -> Unit) {
    val filters = listOf("All", "Continue", "New", "Short")
    var expanded by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val filtered = remember(state.podcasts, state.optimisticPodcasts, state.activeFilter) {
        LibraryFilters.apply(state.activeFilter, state.podcasts + state.optimisticPodcasts)
    }
    Column(Modifier.fillMaxSize()) {
        LazyRow(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filters) { filter ->
                val isSelected = filter == state.activeFilter
                Text(
                    filter, color = if (isSelected) Color(0xFF00F0FF) else Color.White.copy(.7f),
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (isSelected) Color(0xFF00F0FF).copy(.3f) else Color.White.copy(.1f))
                        .clickable { vm.setFilter(filter) }.padding(horizontal = 16.dp, vertical = 12.dp)
                        .semantics { role = Role.Tab; selected = isSelected; contentDescription = "$filter filter" }
                )
            }
        }
        LazyColumn(state = rememberLazyListState(), contentPadding = PaddingValues(bottom = 176.dp, start = 16.dp, end = 16.dp), modifier = Modifier.weight(1f)) {
            if (state.activeFilter == "All") item {
                val hero = state.current ?: state.podcasts.maxByOrNull { it.lastPlayed }
                hero?.let { SpotlightHero(it, state.sleepTimerSeconds, { vm.play(it) }, { vm.addToQueue(it) }, vm::playPreviousEpisode, vm::playNextEpisode, onTimer, { vm.setPlayerOpen(true) }) }
            }
            if (filtered.isEmpty()) item { EmptyState(Icons.Rounded.FilterListOff, if (state.podcasts.isEmpty()) "Build your library" else "No episodes found", "Try a different filter or add more shows.", if (state.podcasts.isEmpty()) "Add podcast" else null, onAdd) }
            val groups = if (state.activeFilter == "All") filtered.groupBy { it.title } else mapOf("Results" to filtered)
            groups.forEach { (title, episodes) ->
                if (episodes.isNotEmpty()) {
                    item(key = "header_$title") {
                        GlassFolderHeader(title, episodes.first().imageUrl, episodes.size, title in expanded, { expanded = if (title in expanded) expanded - title else expanded + title }, { vm.unsubscribe(title) })
                    }
                    if (state.activeFilter != "All" || title in expanded) items(episodes, key = { it.id }) { EpisodeRow(it, state, vm) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeList(episodes: List<PodcastEntity>, state: AppViewModel.UiState, vm: AppViewModel, dismissible: Boolean = false) {
    LazyColumn(contentPadding = PaddingValues(bottom = 176.dp, start = 16.dp, end = 16.dp)) {
        if (episodes.isEmpty()) item { EmptyState(Icons.Rounded.Inbox, "Nothing here", "Episodes will appear here when available.") }
        items(episodes, key = { it.id }) { episode ->
            if (dismissible) {
                val dismissState = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) { vm.removeFromQueue(episode); true } else false
                    }
                )
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Box(Modifier.fillMaxSize().padding(horizontal = 24.dp), Alignment.CenterEnd) {
                            Icon(Icons.Rounded.PlaylistRemove, "Remove from queue", tint = Color.White.copy(.55f))
                        }
                    }
                ) { EpisodeRow(episode, state, vm) }
            } else {
                EpisodeRow(episode, state, vm)
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: PodcastEntity, state: AppViewModel.UiState, vm: AppViewModel) {
    val progressFraction = if (episode.duration > 0) episode.progress.toFloat() / episode.duration else 0f
    val remainingMs = if (progressFraction > 0f && progressFraction < .95f && episode.duration > episode.progress) {
        val speed = if (episode.id == state.current?.id) state.speed else 1f
        ((episode.duration - episode.progress) / speed).toLong()
    } else 0L
    GlassPodcastRow(
        PodcastRowSpec(
            episode.id, episode.episodeTitle, episode.title, episode.imageUrl, episode.isDownloaded, episode.isInQueue,
            progressFraction, remainingMs, downloadOp = state.downloadOps[episode.id] ?: AsyncOp.Idle
        ),
        { vm.play(episode) }, { vm.downloadEpisode(episode.id) },
        { if (episode.isInQueue) vm.removeFromQueue(episode) else vm.addToQueue(episode) },
        { vm.markPlayed(episode) }, { vm.markOlderPlayed(episode) }, { vm.deleteDownload(episode) }, { vm.playNext(episode) }
    )
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Box(Modifier.fillMaxWidth().height(240.dp), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(icon, null, tint = Color.White.copy(.34f), modifier = Modifier.size(52.dp))
            Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(body, color = Color.White.copy(.62f))
            if (actionLabel != null) Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
private fun SleepTimerSheet(currentSeconds: Int, atEnd: Boolean, onDismiss: () -> Unit, onSelectMinutes: (Int) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        PrismaticGlass(Modifier.fillMaxWidth().heightIn(max = 420.dp).imePadding(), RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text("Sleep timer", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                val minutes = (currentSeconds + 59) / 60
                val status = when {
                    atEnd -> "Pausing at the end of the current episode."
                    currentSeconds > 0 -> "Currently $minutes ${if (minutes == 1) "minute" else "minutes"} remaining"
                    else -> "Pause playback after a chosen duration."
                }
                Text(status, color = Color.White.copy(.62f))
                listOf(15, 30, 45, 60, -1, 0).forEach { value ->
                    val label = when (value) { -1 -> "End of episode"; 0 -> "Off"; else -> "$value minutes" }
                    Text(label, color = Color.White, modifier = Modifier.fillMaxWidth().clickable { onSelectMinutes(value) }.padding(14.dp))
                }
            }
        }
    }
}

@Composable
fun AddPodcastDialog(onDismiss: () -> Unit, onImport: (String) -> Unit, onSearch: (String) -> Unit, searchResults: List<ItunesSearchResult>) {
    var text by rememberSaveable { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var importingFeed by rememberSaveable { mutableStateOf<String?>(null) }
    val validUrl = text.startsWith("https://") || text.startsWith("http://")
    Dialog(onDismissRequest = onDismiss) {
        PrismaticGlass(Modifier.fillMaxWidth().heightIn(max = 520.dp).imePadding(), RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(24.dp)) {
                TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                    Tab(selectedTab == 0, { selectedTab = 0 }, text = { Text("Search") })
                    Tab(selectedTab == 1, { selectedTab = 1 }, text = { Text("URL") })
                }
                Spacer(Modifier.height(16.dp))
                if (selectedTab == 0) {
                    OutlinedTextField(text, { text = it; onSearch(it) }, label = { Text("Search for a podcast") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        if (text.isBlank()) item { Text("Type a podcast name to search.", color = Color.White.copy(.6f)) }
                        else if (searchResults.isEmpty()) item { Text("Searching…", color = Color.White.copy(.6f)) }
                        else items(searchResults) { result ->
                            SearchResultRow(result, importingFeed == result.feedUrl) {
                                importingFeed = result.feedUrl
                                onImport(result.feedUrl)
                            }
                        }
                    }
                } else {
                    BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), decorationBox = { inner -> if (text.isEmpty()) Text("https://…", color = Color.Gray); inner() })
                    Text(if (text.isEmpty() || validUrl) "Paste a direct RSS or Atom URL." else "Feed URL must start with http:// or https://", color = Color.White.copy(.6f), modifier = Modifier.padding(vertical = 12.dp))
                    Row(Modifier.fillMaxWidth(), Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Button(enabled = validUrl, onClick = { importingFeed = text; onImport(text) }) { Text(if (importingFeed == text) "Syncing…" else "Import") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: ItunesSearchResult, syncing: Boolean, onSubscribe: () -> Unit) {
    PrismaticGlass(Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 76.dp), RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxSize().clickable(onClick = onSubscribe).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(result.collectionName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                if (result.artistName.isNotBlank()) Text(result.artistName, color = Color.White.copy(.62f), maxLines = 1)
            }
            if (syncing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            else Icon(Icons.Rounded.AddCircle, "Subscribe to ${result.collectionName}", tint = Color.Cyan, modifier = Modifier.size(48.dp).padding(10.dp))
        }
    }
}

class MotionDetector(private val onShake: () -> Unit) : android.hardware.SensorEventListener {
    private var lastUpdate = 0L
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f
    private val shakeThreshold = 2500
    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()
        if (now - lastUpdate > 100) {
            val diff = now - lastUpdate
            lastUpdate = now
            val speed = kotlin.math.abs(event.values[0] + event.values[1] + event.values[2] - lastX - lastY - lastZ) / diff * 10000
            if (speed > shakeThreshold) onShake()
            lastX = event.values[0]; lastY = event.values[1]; lastZ = event.values[2]
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
