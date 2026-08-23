package com.example.alakey.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlayerHost(
    spec: PlayerSpec,
    expanded: Boolean,
    queueCount: Int,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onTogglePlay: () -> Unit,
    onQueue: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSleepTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (expanded) {
        PlayerScreen(spec, onClose, onTogglePlay, onSeek, onSkip, onSetSpeed, onNext, onPrevious, onSleepTimer, modifier.fillMaxSize())
    } else {
        MiniPlayer(spec, queueCount, onOpen, onTogglePlay, onQueue, modifier.padding(bottom = 92.dp, start = 16.dp, end = 16.dp))
    }
}

@Composable
private fun MiniPlayer(spec: PlayerSpec, queueCount: Int, onOpen: () -> Unit, onToggle: () -> Unit, onQueue: () -> Unit, modifier: Modifier) {
    PrismaticGlass(modifier.fillMaxWidth().heightIn(min = 76.dp), RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxSize().clickable(onClick = onOpen).semantics { contentDescription = "Open player for ${spec.title}" }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(spec.imageUrl, null, Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(spec.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(spec.artist, color = Color.White.copy(.58f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                LinearProgressIndicator({ (spec.currentMs.toFloat() / spec.durationMs.coerceAtLeast(1)).coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp), Color(spec.vibrantColor), Color.White.copy(.12f))
            }
            IconButton(onClick = onQueue, modifier = Modifier.size(48.dp)) {
                BadgedBox({ if (queueCount > 0) Badge { Text(queueCount.toString()) } }) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Open queue", tint = Color.White) }
            }
            MorphingPlayPauseButton(spec.isPlaying, onToggle, Color.White, Modifier.size(48.dp).padding(10.dp))
        }
    }
}

@Composable
private fun PlayerScreen(
    spec: PlayerSpec,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSleepTimer: () -> Unit,
    modifier: Modifier
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier.background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).offset { androidx.compose.ui.unit.IntOffset(0, offset.value.roundToInt()) }
            .draggable(rememberDraggableState { delta -> scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) } }, Orientation.Vertical, onDragStopped = { velocity ->
                if (shouldDismissPlayer(offset.value, velocity)) onClose() else scope.launch { offset.animateTo(0f, spring()) }
            })
    ) {
        FluxBackground(amplitude = spec.amplitude, color = Color(spec.dominantColor))
        val landscape = maxWidth > maxHeight
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerArtwork(spec, Modifier.weight(.42f).fillMaxHeight())
                PlayerDetails(spec, onToggle, onSeek, onSkip, onSetSpeed, onNext, onPrevious, onSleepTimer, Modifier.weight(.58f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CloseHandle(onClose)
                PlayerArtwork(spec, Modifier.fillMaxWidth().weight(.48f))
                PlayerDetails(spec, onToggle, onSeek, onSkip, onSetSpeed, onNext, onPrevious, onSleepTimer, Modifier.fillMaxWidth().weight(.52f))
            }
        }
    }
}

@Composable
private fun CloseHandle(onClose: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(onClick = onClose).semantics { role = Role.Button }, Alignment.Center) {
        Box(Modifier.width(40.dp).height(4.dp).background(Color.White.copy(.45f), CircleShape))
    }
}

@Composable
private fun PlayerArtwork(spec: PlayerSpec, modifier: Modifier) {
    Box(modifier.padding(12.dp), Alignment.Center) {
        AsyncImage(spec.imageUrl, "Episode artwork", Modifier.fillMaxSize(.84f).aspectRatio(1f).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
        if (spec.sleepTimerSeconds > 0) {
            Surface(color = Color.Black.copy(.75f), shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd)) {
                Text(formatMs(spec.sleepTimerSeconds * 1000L), color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun PlayerDetails(spec: PlayerSpec, onToggle: () -> Unit, onSeek: (Long) -> Unit, onSkip: (Int) -> Unit, onSetSpeed: (Float) -> Unit, onNext: () -> Unit, onPrevious: () -> Unit, onSleepTimer: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        NebulaText(spec.title, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        Text(spec.artist, color = Color.White.copy(.7f), maxLines = 1)
        PlayerScrubber(spec.currentMs, spec.durationMs, spec.vibrantColor, onSeek)
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            PlayerIconButton(Icons.Rounded.SkipPrevious, "Previous episode", onPrevious)
            PlayerIconButton(Icons.Rounded.Replay30, "Skip back 30 seconds") { onSkip(-PlayerTokens.SKIP_BACK_SECONDS) }
            Box(Modifier.size(72.dp).background(Color.White, CircleShape), Alignment.Center) { MorphingPlayPauseButton(spec.isPlaying, onToggle, Color.Black, Modifier.size(56.dp).padding(14.dp)) }
            PlayerIconButton(Icons.Rounded.Forward30, "Skip forward 30 seconds") { onSkip(PlayerTokens.SKIP_FORWARD_SECONDS) }
            PlayerIconButton(Icons.Rounded.SkipNext, "Next episode", onNext)
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            TextButton(onClick = { onSetSpeed(if (spec.speed >= 2f) .5f else spec.speed + .5f) }) { Text("${spec.speed}×", color = Color.White) }
            PlayerIconButton(Icons.Rounded.Timer, "Sleep timer", onSleepTimer)
        }
    }
}

@Composable
private fun PlayerScrubber(position: Long, duration: Long, vibrantColor: Int, onCommit: (Long) -> Unit) {
    var local by remember { mutableStateOf<Float?>(null) }
    Slider(
        value = local ?: position.toFloat(),
        onValueChange = { local = it },
        onValueChangeFinished = { local?.let { onCommit(it.toLong()) }; local = null },
        valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
        modifier = Modifier.fillMaxWidth(),
        colors = SliderDefaults.colors(activeTrackColor = Color(vibrantColor))
    )
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
        Text(formatMs((local ?: position.toFloat()).toLong()), color = Color.White.copy(.6f), style = MaterialTheme.typography.labelSmall)
        Text(formatMs(duration), color = Color.White.copy(.6f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PlayerIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(48.dp)) { Icon(icon, label, tint = Color.White) }
}

fun formatMs(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    else String.format(Locale.US, "%d:%02d", minutes, secs)
}

@Composable
fun MorphingPlayPauseButton(isPlaying: Boolean, onToggle: () -> Unit, tint: Color, modifier: Modifier = Modifier) {
    val transition = updateTransition(isPlaying, label = "play_pause")
    val t by transition.animateFloat({ tween(180, easing = FastOutSlowInEasing) }, label = "progress") { if (it) 1f else 0f }
    val path = remember { Path() }
    Canvas(modifier.clickable(onClick = onToggle).semantics { role = Role.Button; stateDescription = if (isPlaying) "Playing" else "Paused" }) {
        val barWidth = size.width * .3f
        path.reset(); path.moveTo(0f, 0f); path.lineTo(lerp(size.width, barWidth, t), lerp(size.height / 2f, 0f, t)); path.lineTo(lerp(size.width, barWidth, t), lerp(size.height / 2f, size.height, t)); path.lineTo(0f, size.height); path.close()
        drawPath(path, tint)
        if (t > 0f) drawRect(tint, Offset(size.width - barWidth, 0f), Size(barWidth, size.height), alpha = t)
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float) = (1 - fraction) * start + fraction * stop
