package com.example.alakey.ui

import android.Manifest
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.alakey.data.ItunesSearchResult
import com.example.alakey.data.PodcastEntity
import android.bluetooth.BluetoothHeadset

@Composable
fun MainContent() {
    val vm: AppViewModel = hiltViewModel()
    val state by vm.uiState.collectAsState()
    val searchResults by vm.searchResults.collectAsState()
    val logs by vm.logs.collectAsState()
    val sleepTimerSeconds by vm.sleepTimerSeconds.collectAsState()
    val context = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    
    // Smart Sleep Timer
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val motionDetector = remember { 
        MotionDetector { 
           vm.resetSleepTimer() 
           Toast.makeText(context, "Sleep Timer Extended", Toast.LENGTH_SHORT).show()
        } 
    }

    DisposableEffect(sleepTimerSeconds > 0) {
        if (sleepTimerSeconds > 0) {
            sensorManager.registerListener(motionDetector, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            sensorManager.unregisterListener(motionDetector)
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        vm.connect()
        vm.checkForAutoDownloads()
        vm.userEvents.collect { event ->
            when (event) {
                is AppViewModel.UserEvent.ShowMessage -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                is AppViewModel.UserEvent.ShowError -> Toast.makeText(context, "Error: ${event.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Phase 4: Simplicity - Declarative Back Logic
    BackHandler(enabled = state.navigationStack.size > 1 || state.isPlayerOpen) {
        if (state.isPlayerOpen) {
            vm.dispatch(AppViewModel.Action.SetPlayerOpen(false))
        } else {
            vm.dispatch(AppViewModel.Action.Pop)
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_HEADSET_PLUG || intent.action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED) {
                     vm.resumePlayback()
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

    var showAddDialog by remember { mutableStateOf(false) }
    var showDebug by remember { mutableStateOf(false) } // Debug Mode
    var showSleepSheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        FluxBackground(amplitude = state.amplitude, color = Color(state.dominantColor))
        // Debug Trigger (Invisible top left)
        Box(Modifier.size(64.dp).align(Alignment.TopStart).zIndex(10f).clickable { showDebug = !showDebug })
        // Dynamic Header Area
        val listState = rememberLazyListState()
        val activeScreen = state.navigationStack.last()
        
        Column(Modifier.fillMaxSize()) {
            // Header (Title + Actions)
            Row(
           Modifier
               .fillMaxWidth()
               .padding(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 16.dp),
           horizontalArrangement = Arrangement.SpaceBetween,
           verticalAlignment = Alignment.CenterVertically
        ) {
            val title = activeScreen.name
            Text(
                title, 
                color = Color.White, 
                style = MaterialTheme.typography.displaySmall, 
                fontWeight = FontWeight.Bold,
                modifier = Modifier.shadow(20.dp, spotColor = Color(0xFF00F0FF))
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                 // Radio FAB (Mini)
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFF0055FF))))
                        .clickable { vm.playRadio() }
                        .padding(8.dp)
                ) {
                    Icon(Icons.Rounded.Radio, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                
                Spacer(Modifier.width(12.dp))
                
                IconButton(onClick = { vm.setCarMode(true) }, modifier = Modifier.background(Color.White.copy(0.1f), CircleShape)) {
                    Icon(Icons.Rounded.DirectionsCar, null, tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { showAddDialog = true }, modifier = Modifier.background(Color.White.copy(0.1f), CircleShape)) { 
                    Icon(Icons.Rounded.Add, null, tint = Color.White) 
                }
            }
        }        
    
    AnimatedContent(
        targetState = activeScreen,
        transitionSpec = {
            (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut(animationSpec = tween(200)))
        },
        label = "screen_transition",
        modifier = Modifier.weight(1f)
    ) { currentScreen ->
        if (currentScreen == AppViewModel.Screen.Library) {
           val filters = listOf("All", "Continue", "New", "Short")

           Column(Modifier.fillMaxSize()) {
               LazyRow(
               modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
               contentPadding = PaddingValues(horizontal = 24.dp),
               horizontalArrangement = Arrangement.spacedBy(8.dp)
           ) {
               items(filters) { f ->
                   val isActive = state.activeFilter == f
                   Box(Modifier.clip(RoundedCornerShape(50)).background(if(isActive) Color(0xFF00F0FF).copy(0.3f) else Color.White.copy(0.1f)).pressScale().clickable { vm.setFilter(f) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                       Text(f, color = if(isActive) Color(0xFF00F0FF) else Color.White.copy(0.7f), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                   }
               }
           }

            val filteredPodcasts = remember(state.podcasts, state.optimisticPodcasts, state.activeFilter) {
                val allPodcasts = (state.podcasts + state.optimisticPodcasts)
                when(state.activeFilter) {
                    "Continue" -> allPodcasts.filter { p -> 
                        val duration = if (p.duration > 0) p.duration.toDouble() else Double.MAX_VALUE
                        p.progress > 0 && p.progress.toDouble() < (duration * 0.95)
                    }.sortedByDescending { it.lastPlayed }
                    "New" -> allPodcasts.filter { p ->
                           val duration = if (p.duration > 0) p.duration.toDouble() else Double.MAX_VALUE
                           p.progress.toDouble() < (duration * 0.95)
                    }.sortedByDescending { it.pubDate } 
                    "Short" -> allPodcasts.filter { 
                        val duration = if (it.duration > 0) it.duration.toDouble() else Double.MAX_VALUE
                        it.progress.toDouble() < (duration * 0.95) && it.duration in 1..1200 
                    }
                    else -> allPodcasts.filter { p ->
                        val duration = if (p.duration > 0) p.duration.toDouble() else Double.MAX_VALUE
                        p.progress.toDouble() < (duration * 0.95)
                    }
                }
            }
           val expandedGroups = remember { mutableStateListOf<String>() }
           
           AnimatedContent(
               targetState = state.activeFilter,
               transitionSpec = {
                   (fadeIn(animationSpec = tween(300)) + scaleIn(initialScale = 0.95f)).togetherWith(fadeOut(animationSpec = tween(200)))
               },
               label = "filter_transition",
               modifier = Modifier.weight(1f)
            ) { filter ->
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 0.dp, bottom = 176.dp, start = 16.dp, end = 16.dp)
                ) {
                    // Hero Item
                    if (filter == "All") {
                        item {
                            val heroPodcast = state.current ?: state.podcasts.maxByOrNull { it.lastPlayed } ?: state.podcasts.firstOrNull()
                            
                            SpotlightHero(
                                podcast = heroPodcast, 
                                timerSeconds = state.sleepTimerSeconds,
                                onPlay = { if (heroPodcast != null) vm.dispatch(AppViewModel.Action.Play(heroPodcast)) },
                                onQueue = { if (heroPodcast != null) vm.addToQueue(heroPodcast) },
                                onPrev = { vm.dispatch(AppViewModel.Action.PlayPreviousInQueue) },
                                onNext = { vm.dispatch(AppViewModel.Action.PlayNextInQueue) },
                                onTimer = { showSleepSheet = true },
                                onClick = { if (heroPodcast != null) vm.setPlayerOpen(true) }
                            )
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    if (filteredPodcasts.isEmpty()) {
                       item { 
                           EmptyState(
                               icon = Icons.Rounded.FilterListOff,
                               title = if (state.podcasts.isEmpty()) "Build your library" else "No episodes found",
                               body = if (state.podcasts.isEmpty()) "Search the marketplace or paste an RSS feed to start listening." else "Try a different filter or add more shows.",
                               actionLabel = if (state.podcasts.isEmpty()) "Add podcast" else null,
                               onAction = { showAddDialog = true }
                           )
                           /*Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { 
                               Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                   Icon(Icons.Rounded.FilterListOff, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(48.dp))
                                   Spacer(Modifier.height(16.dp))
                                   NebulaText("No episodes found", MaterialTheme.typography.bodyLarge, glowColor = Color.Transparent) 
                               } 
                           }*/
                       }
                   } else {
                       val grouped = if(state.activeFilter == "All") filteredPodcasts.groupBy { it.title } else mapOf("Results" to filteredPodcasts)
                       
                       grouped.forEach { (title, eps) ->
                           if (eps.isNotEmpty()) {
                               // Header
                               item(key = "header_$title") {
                                   GlassFolderHeader(
                                       title = title,
                                       imageUrl = eps.first().imageUrl,
                                       count = eps.size,
                                       isExpanded = expandedGroups.contains(title),
                                       onToggle = { 
                                           if (expandedGroups.contains(title)) expandedGroups.remove(title) else expandedGroups.add(title) 
                                       },
                                       onUnsubscribe = { vm.unsubscribe(title) }
                                   )
                               }
                               
                               // Episodes (if expanded)
                               if (expandedGroups.contains(title) || state.activeFilter != "All") {
                                   items(items = eps, key = { it.id }) { ep ->
                                       Box(Modifier.padding(start = 24.dp)) {
                                           GlassPodcastRow(
                                               spec = PodcastRowSpec(
                                                   id = ep.id,
                                                   title = ep.episodeTitle,
                                                   subtitle = ep.title,
                                                   imageUrl = ep.imageUrl,
                                                   isDownloaded = ep.isDownloaded,
                                                   isInQueue = ep.isInQueue,
                                                   progress = if(ep.duration>0) ep.progress.toFloat()/ep.duration else 0f
                                               ),
                                               onClick = { vm.play(ep); vm.setPlayerOpen(true) },
                                               onDownload = { vm.downloadEpisode(ep.id) },
                                               onAddToQueue = { 
                                                   if (ep.isInQueue) vm.removeFromQueue(ep) else vm.addToQueue(ep)
                                               },
                                               onMarkPlayed = { vm.markPlayed(ep) },
                                               onArchiveOlder = { vm.markOlderPlayed(ep) },
                                               onDeleteDownload = { vm.deleteDownload(ep) },
                                               onPlayNext = { vm.playNext(ep) }
                                           )
                                       }
                                   }
                               }
                           }
                       }
                   }
               }
           }
           } // Closing brace for the added Column
          } else if (currentScreen == AppViewModel.Screen.Inbox) {
          // INBOX VIEW
          val inbox = state.inbox
           LazyColumn(contentPadding = PaddingValues(top = 24.dp, bottom = 176.dp, start = 16.dp, end = 16.dp)) {
               if (inbox.isEmpty()) {
                    item { 
                       EmptyState(
                           icon = Icons.Rounded.Inbox,
                           title = "All caught up",
                           body = "New unplayed episodes will appear here after your feeds sync.",
                           actionLabel = "Discover shows",
                           onAction = { vm.navigate(AppViewModel.Screen.Marketplace) }
                       )
                    }
              } else {
                  items(items = inbox, key = { it.id }) { ep ->
                      Box(Modifier.padding(bottom=8.dp)) {
                          GlassPodcastRow(
                              spec = PodcastRowSpec(
                                  id = ep.id,
                                  title = ep.episodeTitle,
                                  subtitle = ep.title,
                                  imageUrl = ep.imageUrl,
                                  isDownloaded = ep.isDownloaded,
                                  isInQueue = ep.isInQueue,
                                  progress = if(ep.duration>0) ep.progress.toFloat()/ep.duration else 0f
                              ),
                              onClick = { vm.play(ep); vm.setPlayerOpen(true) },
                              onDownload = { vm.downloadEpisode(ep.id) },
                              onAddToQueue = { 
                                  vm.addToQueue(ep) 
                              },
                              onMarkPlayed = { vm.markPlayed(ep) },
                              onArchiveOlder = { vm.markOlderPlayed(ep) },
                              onDeleteDownload = { vm.deleteDownload(ep) },
                              onPlayNext = { vm.playNext(ep) }
                          )
                      }
                  }
              }
          }
    } else if (currentScreen == AppViewModel.Screen.Queue) {
          val queue = state.queue
          LazyColumn(contentPadding = PaddingValues(top = 24.dp, bottom = 176.dp, start = 16.dp, end = 16.dp)) {
              if (queue.isEmpty()) {
                  item {
                      EmptyState(
                          icon = Icons.AutoMirrored.Rounded.QueueMusic,
                          title = "Queue is empty",
                          body = "Long-press an episode or tap its queue action to line up listening.",
                          actionLabel = "Back to library",
                          onAction = { vm.navigate(AppViewModel.Screen.Library) }
                      )
                  }
              } else {
                  item {
                      Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                          Text("Up next", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                          Text("${queue.size} queued", color = Color(0xFFBD00FF), style = MaterialTheme.typography.labelLarge)
                      }
                  }
                  items(items = queue, key = { it.id }) { ep ->
                      GlassPodcastRow(
                          spec = PodcastRowSpec(
                              id = ep.id,
                              title = ep.episodeTitle,
                              subtitle = ep.title,
                              imageUrl = ep.imageUrl,
                              isDownloaded = ep.isDownloaded,
                              isInQueue = ep.isInQueue,
                              progress = if (ep.duration > 0) ep.progress.toFloat() / ep.duration else 0f
                          ),
                          onClick = { vm.play(ep); vm.setPlayerOpen(true) },
                          onDownload = { vm.downloadEpisode(ep.id) },
                          onAddToQueue = { vm.removeFromQueue(ep) },
                          onMarkPlayed = { vm.markPlayed(ep) },
                          onArchiveOlder = { vm.markOlderPlayed(ep) },
                          onDeleteDownload = { vm.deleteDownload(ep) },
                          onPlayNext = { vm.playNext(ep) }
                      )
                  }
              }
          }
    } else {
        GlassMarketplace(onSubscribe = { query -> 
            vm.marketplaceSubscribe(query)
        })
    }
}
    
        }
    
    // --- Flux Player Continuum ---
    val expansion by animateFloatAsState(
        targetValue = if (state.isPlayerOpen) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "expansion"
    )
       if (state.current != null) {
            val playerSpec = PlayerSpec(
                title = state.current!!.episodeTitle,
                artist = state.current!!.title,
                imageUrl = state.current!!.imageUrl,
                isPlaying = state.isPlaying,
                currentMs = state.currentTime,
                durationMs = state.duration,
                speed = state.speed,
                amplitude = state.amplitude,
                sleepTimerSeconds = sleepTimerSeconds,
                dominantColor = state.dominantColor,
                vibrantColor = state.vibrantColor,
                mutedColor = state.mutedColor
            )
            
            FluxPlayerContinuum(
                expansion = expansion,
                spec = playerSpec,
                onTogglePlay = { vm.togglePlay() },
                onClick = { vm.setPlayerOpen(true) },
                onClose = { vm.setPlayerOpen(false) },
                onSeek = { vm.seek(it) },
                onSkip = { vm.skip(it) },
                onSetSpeed = { vm.setPlaybackSpeed(it) },
                onNext = { vm.playNextEpisode() },
                onPrev = { vm.playPreviousEpisode() },
                onSleepTimer = { showSleepSheet = true }
            )
        }

        if (state.current != null && !state.isPlayerOpen && !state.isCarMode) {
            MiniPlayerDock(
                spec = PlayerSpec(
                    title = state.current!!.episodeTitle,
                    artist = state.current!!.title,
                    imageUrl = state.current!!.imageUrl,
                    isPlaying = state.isPlaying,
                    currentMs = state.currentTime,
                    durationMs = state.duration,
                    speed = state.speed,
                    sleepTimerSeconds = sleepTimerSeconds,
                    vibrantColor = state.vibrantColor
                ),
                queueCount = state.queue.size,
                onClick = { vm.setPlayerOpen(true) },
                onTogglePlay = { vm.togglePlay() },
                onQueue = { vm.navigate(AppViewModel.Screen.Queue) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp, start = 16.dp, end = 16.dp)
            )
        }
        
        // Glass Dock (Bottom Navigation)
        if (!state.isPlayerOpen && !state.isCarMode) {
             Box(Modifier.align(Alignment.BottomCenter)) {
                 GlassDock(
                     currentScreen = activeScreen,
                     onNavigate = { vm.navigate(it) }
                 )
             }
        }
    }
    
    if (state.isCarMode) {
        CarModeScreen(
            spec = if (state.current != null) PlayerSpec(
                title = state.current!!.episodeTitle,
                artist = state.current!!.title,
                imageUrl = state.current!!.imageUrl,
                isPlaying = state.isPlaying,
                currentMs = state.currentTime,
                durationMs = state.duration,
                speed = state.speed,
                amplitude = state.amplitude,
                sleepTimerSeconds = sleepTimerSeconds,
                dominantColor = state.dominantColor
            ) else null,
            onTogglePlay = { vm.togglePlay() },
            onSkipForward = { vm.skip(30) },
            onSkipBack = { vm.skip(-15) },
            onExit = { vm.setCarMode(false) }
        )
    }

    if (showAddDialog) {
        AddPodcastDialog(
            onDismiss = { showAddDialog = false },
            onImport = { url ->
                vm.importFeed(url)
                showAddDialog = false
            },
            onSearch = { vm.searchPodcasts(it) },
            searchResults = searchResults
        )
    }

    if (showSleepSheet) {
        SleepTimerSheet(
            currentSeconds = sleepTimerSeconds,
            onDismiss = { showSleepSheet = false },
            onSelectMinutes = { minutes ->
                if (minutes == 0) vm.cancelSleepTimer() else vm.startSleepTimer(minutes)
                showSleepSheet = false
            }
        )
    }

    if (showDebug) {
        DebugOverlay(
            historySize = vm.history.size,
            onTimeTravel = { vm.travelTo(it) },
            logs = logs
        )
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(icon, null, tint = Color.White.copy(0.34f), modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(16.dp))
            NebulaText(title, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), glowColor = Color.Transparent)
            Spacer(Modifier.height(8.dp))
            Text(body, color = Color.White.copy(0.62f), style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null) {
                Spacer(Modifier.height(18.dp))
                Button(onClick = onAction, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF), contentColor = Color.Black)) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerDock(
    spec: PlayerSpec,
    queueCount: Int,
    onClick: () -> Unit,
    onTogglePlay: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    PrismaticGlass(modifier.fillMaxWidth().height(76.dp), RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxSize().clickable { onClick() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(spec.imageUrl, null, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(spec.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(spec.artist, color = Color.White.copy(0.58f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                LinearProgressIndicator(
                    progress = { (spec.currentMs.toFloat() / spec.durationMs.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(CircleShape),
                    color = Color(spec.vibrantColor),
                    trackColor = Color.White.copy(0.12f)
                )
            }
            IconButton(onClick = onQueue) {
                BadgedBox(badge = { if (queueCount > 0) Badge { Text(queueCount.toString()) } }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = Color.White.copy(0.82f))
                }
            }
            MorphingPlayPauseButton(spec.isPlaying, onTogglePlay, Modifier.size(34.dp))
        }
    }
}

@Composable
private fun SleepTimerSheet(currentSeconds: Int, onDismiss: () -> Unit, onSelectMinutes: (Int) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        PrismaticGlass(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(22.dp)) {
                Text("Sleep timer", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(if (currentSeconds > 0) "Currently ${currentSeconds / 60} minutes remaining" else "Pause playback after a chosen duration.", color = Color.White.copy(0.62f))
                Spacer(Modifier.height(18.dp))
                listOf(15, 30, 45, 60, 0).forEach { minutes ->
                    val label = if (minutes == 0) "Off" else "$minutes minutes"
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onSelectMinutes(minutes) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (minutes == 0) Icons.Rounded.TimerOff else Icons.Rounded.Timer, null, tint = if (minutes == 0) Color.White.copy(0.5f) else Color(0xFF00F0FF))
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}



@Composable
fun AddPodcastDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
    onSearch: (String) -> Unit,
    searchResults: List<ItunesSearchResult>
) {
    var text by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    var importingFeed by remember { mutableStateOf<String?>(null) }
    val isValidFeedUrl = text.startsWith("https://") || text.startsWith("http://")

    Dialog(onDismissRequest = onDismiss) {
        PrismaticGlass(Modifier.fillMaxWidth().height(400.dp), RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(24.dp)) {
                TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, contentColor = Color.White) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Search") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("URL") })
                }
                Spacer(Modifier.height(16.dp))
                if (selectedTab == 0) {
                    Column {
                        OutlinedTextField(value = text, onValueChange = { text = it; onSearch(it) }, label = { Text("Search for a podcast") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Spacer(Modifier.height(16.dp))
                        LazyColumn {
                            items(searchResults) { result ->
                                Box(Modifier.padding(bottom=8.dp)) {
                                    val isSyncing = importingFeed == result.feedUrl
                                    GlassPodcastRow(
                                        spec = PodcastRowSpec(
                                            id = result.feedUrl, // No dedicated ID for search result item, use feed
                                            title = result.collectionName,
                                            subtitle = "", // artistName not available in ItunesSearchResult mapping
                                            imageUrl = result.artworkUrl100,
                                            isSyncing = isSyncing
                                        ),
                                        onClick = { importingFeed = result.feedUrl; onImport(result.feedUrl) }, // Tap adds feed
                                        onDownload = { /* No-op, or preview? */ },
                                        onAddToQueue = { importingFeed = result.feedUrl; onImport(result.feedUrl) } // Add = Subscribe
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.Center) {
                        NebulaText("Add Feed", MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))
                        Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(0.3f)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) { 
                            BasicTextField(value = text, onValueChange = { text = it }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), singleLine = true, decorationBox = { if (text.isEmpty()) Text("https://...", color = Color.Gray); it() }, modifier = Modifier.fillMaxWidth()) 
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (text.isEmpty() || isValidFeedUrl) "Paste a direct RSS or Atom feed URL." else "Feed URL must start with http:// or https://",
                            color = if (text.isEmpty() || isValidFeedUrl) Color.White.copy(0.55f) else Color(0xFFFF6B6B),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) { 
                            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(0.6f)) }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                enabled = isValidFeedUrl,
                                onClick = { importingFeed = text; onImport(text) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00F0FF), contentColor = Color.Black)
                            ) { Text(if (importingFeed == text) "Syncing..." else "Import") }
                        }
                    }
                }
            }
        }
    }
}


class MotionDetector(private val onShake: () -> Unit) : android.hardware.SensorEventListener {
    private var lastUpdate: Long = 0
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    private val SHAKE_THRESHOLD = 800

    override fun onSensorChanged(event: android.hardware.SensorEvent?) {
        if (event == null) return
        val curTime = System.currentTimeMillis()
        if ((curTime - lastUpdate) > 100) {
            val diffTime = (curTime - lastUpdate)
            lastUpdate = curTime
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val speed = Math.abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000
            if (speed > SHAKE_THRESHOLD) {
                onShake()
            }
            lastX = x
            lastY = y
            lastZ = z
        }
    }
    
    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
}
