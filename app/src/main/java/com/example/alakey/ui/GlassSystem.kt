package com.example.alakey.ui

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import coil.compose.AsyncImage
import com.example.alakey.data.PodcastEntity
import com.example.alakey.ui.theme.LIQUID_PLASMA_SRC

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset


@Composable
fun FluxBackground(modifier: Modifier = Modifier, amplitude: Float = 1f, color: Color = Color.Cyan) {
    if (Build.VERSION.SDK_INT >= 33) {
        val infiniteTransition = rememberInfiniteTransition(label = "time")
        val time by infiniteTransition.animateFloat(
            initialValue = 0f, 
            targetValue = 30f, 
            animationSpec = infiniteRepeatable(tween(45000, easing = LinearEasing)), 
            label = "t"
        )
        val shader = remember { RuntimeShader(LIQUID_PLASMA_SRC) }
        // Composite time: Increase speed with amplitude for "Excitement"
        val compositeTime = time * (1.0f + amplitude * 2.0f)
        
        Canvas(modifier.fillMaxSize()) {
            shader.setFloatUniform("resolution", size.width, size.height)
            shader.setFloatUniform("time", compositeTime)
            // Color Intensity: Brighter when loud
            val glowAlpha = 0.1f + amplitude * 0.2f
            
            drawRect(brush = ShaderBrush(shader))
            drawRect(brush = Brush.radialGradient(listOf(color.copy(glowAlpha), Color.Transparent), radius = size.maxDimension), blendMode = BlendMode.Screen)
        }
    } else { Box(modifier.fillMaxSize().background(Color(0xFF020024))) }
}

fun Modifier.pressScale(targetScale: Float = 0.95f) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "press_scale"
    )
    this.scale(scale).clickable(interactionSource = interactionSource, indication = null) { }
}

fun Modifier.glassShimmer() = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "shimmer_x"
    )
    this.drawWithCache {
        val shimmerBrush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(0.08f), Color.Transparent),
            start = Offset(size.width * xOffset, 0f),
            end = Offset(size.width * xOffset + 100f, size.height)
        )
        onDrawWithContent { drawContent(); drawRect(shimmerBrush) }
    }
}

fun Modifier.inertialTilt(maxRotation: Float = 12f) = composed {
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    this.graphicsLayer { rotationX = rotX.value; rotationY = rotY.value; cameraDistance = 16f * density }.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = { scope.launch { rotX.animateTo(0f, spring(0.4f, 200f)); rotY.animateTo(0f, spring(0.4f, 200f)) } },
            onDrag = { change, dragAmount ->
                change.consume()
                scope.launch {
                    val tx = (rotX.value - dragAmount.y * 0.15f).coerceIn(-maxRotation, maxRotation)
                    val ty = (rotY.value + dragAmount.x * 0.15f).coerceIn(-maxRotation, maxRotation)
                    rotX.snapTo(tx); rotY.snapTo(ty)
                }
            }
        )
    }
}

@Composable
fun PrismaticGlass(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(24.dp), content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current
    Box(modifier.clip(shape).background(Color.White.copy(alpha = 0.05f)).drawWithCache {
        val path = Path().apply { addRoundRect(RoundRect(size.toRect(), CornerRadius(shape.topStart.toPx(size, density)))) }
        val spectrum = Brush.sweepGradient(listOf(Color.Cyan.copy(0.6f), Color(0xFFBD00FF).copy(0.6f), Color.Yellow.copy(0.4f), Color.Cyan.copy(0.6f)))
        onDrawWithContent { drawContent(); drawPath(path, spectrum, style = Stroke(width = 1.2.dp.toPx())) }
    }, content = content)
}

@Composable
fun NebulaText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, glowColor: Color = Color.Cyan, speed: Float = 1.0f) {
    val dynamicWeight = remember(speed) {
        when {
            speed < 0.9f -> FontWeight.Light
            speed > 1.4f -> FontWeight.Black
            speed > 1.1f -> FontWeight.Bold
            else -> style.fontWeight ?: FontWeight.Normal
        }
    }
    
    val dynamicSpacing = remember(speed) {
        when {
            speed < 0.9f -> 2.sp // Airy
            speed > 1.4f -> (-0.5).sp // Urgent
            else -> style.letterSpacing
        }
    }
    
    Box(modifier) {
        if (Build.VERSION.SDK_INT >= 31) { 
            androidx.compose.material3.Text(
                text = text, 
                style = style.copy(fontWeight = dynamicWeight, letterSpacing = dynamicSpacing), 
                color = glowColor.copy(0.8f), 
                modifier = Modifier.graphicsLayer { renderEffect = RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.DECAL).asComposeRenderEffect() }
            ) 
        }
        androidx.compose.material3.Text(text = text, style = style.copy(fontWeight = dynamicWeight, letterSpacing = dynamicSpacing), color = Color.White.copy(0.98f))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassPodcastRow(
    spec: PodcastRowSpec, 
    onClick: () -> Unit, 
    onDownload: () -> Unit, 
    onAddToQueue: () -> Unit,
    onMarkPlayed: () -> Unit = {},
    onArchiveOlder: () -> Unit = {},
    onDeleteDownload: () -> Unit = {},
    onPlayNext: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    PrismaticGlass(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(88.dp).glassShimmer()) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { showMenu = true }
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(spec.imageUrl, null, Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    NebulaText(spec.title, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(4.dp))
                    Text(spec.subtitle, color = Color.White.copy(0.62f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (spec.progress > 0f) StatusPill(if (spec.progress >= 0.95f) "Played" else "${(spec.progress * 100).roundToInt()}%", Color(0xFFFFB300))
                        if (spec.isInQueue) StatusPill("Queued", Color(0xFFBD00FF))
                        if (spec.isDownloaded) StatusPill("Offline", Color(0xFF00E676))
                        if (spec.isSyncing) StatusPill("Syncing", Color(0xFF00F0FF))
                    }
                }
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color.Black.copy(0.9f))
                ) {
                    DropdownMenuItem(
                        text = { Text(if (spec.isInQueue) "Remove from Queue" else "Add to Queue", color = Color.White) },
                        onClick = { onAddToQueue(); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Play Next", color = Color.White) },
                        onClick = { onPlayNext(); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Mark Played", color = Color.White) },
                        onClick = { onMarkPlayed(); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Archive All Older", color = Color.White) },
                        onClick = { onArchiveOlder(); showMenu = false }
                    )
                    if (spec.isDownloaded) {
                        DropdownMenuItem(
                            text = { Text("Delete Download", color = Color.Red) },
                            onClick = { onDeleteDownload(); showMenu = false }
                        )
                    }
                }
            }
            
            Row(Modifier.padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    spec.isSyncing -> CircularProgressIndicator(color = Color.Cyan, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    spec.isDownloaded -> Icon(Icons.Rounded.CheckCircle, null, tint = Color.Green.copy(0.75f), modifier = Modifier.size(24.dp))
                    spec.isInQueue -> IconButton(onClick = onAddToQueue) {
                        Icon(Icons.Rounded.PlaylistRemove, null, tint = Color(0xFFBD00FF))
                    }
                    spec.progress > 0f -> IconButton(onClick = onClick) {
                        Icon(Icons.Rounded.PlayCircle, null, tint = Color(0xFFFFB300))
                    }
                    else -> IconButton(onClick = onDownload) {
                        Icon(Icons.Rounded.CloudDownload, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(Modifier.clip(CircleShape).background(color.copy(0.16f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}


@Composable
fun FluxPlayerContinuum(
    expansion: Float, // 0.0 to 1.0 (0=Mini, 1=Full)
    spec: PlayerSpec,
    onTogglePlay: () -> Unit,
    onClick: () -> Unit,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onNext: () -> Unit = {},
    onPrev: () -> Unit = {},
    onSleepTimer: () -> Unit = {}
) {
    
    // Geometry Morphing
    val cornerRadius = lerp(12f, 32f, expansion).coerceAtLeast(0f).dp
    val padding = lerp(8f, 0f, expansion).coerceAtLeast(0f).dp
    
    Box(
        Modifier
            .fillMaxSize()
            .padding(bottom = lerp(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value, 0f, expansion).coerceAtLeast(0f).dp)
    ) {
        // Shared Glass Surface
        PrismaticGlass(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(lerp(0.08f, 1.0f, expansion).coerceIn(0f, 1f)) // Approx 64dp to full
                .padding(padding),
            shape = RoundedCornerShape(cornerRadius)
        ) {
            Box(Modifier.fillMaxSize()) {
                if (expansion < 0.5f) {
                    // Mini Layout
                    Row(
                        Modifier
                            .fillMaxSize()
                            .clickable(onClick = onClick)
                            .padding(8.dp)
                            .graphicsLayer { this.alpha = 1f - (expansion * 2f) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(spec.imageUrl, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(spec.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(spec.artist, color = Color.LightGray, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            LinearProgressIndicator(
                                progress = { (spec.currentMs.toFloat() / spec.durationMs.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(CircleShape),
                                color = Color(spec.vibrantColor),
                                trackColor = Color.White.copy(0.14f)
                            )
                        }
                        if (spec.sleepTimerSeconds > 0) {
                            Text("${spec.sleepTimerSeconds / 60}m", color = Color(0xFF00F0FF), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                        }
                        MorphingPlayPauseButton(spec.isPlaying, { onTogglePlay() }, Modifier.size(40.dp))
                    }
                } else {
                    Box(Modifier.graphicsLayer { this.alpha = (expansion - 0.5f) * 2f }) {
                        GlassPlayerMechanism(spec, 0f, onClose, onTogglePlay, onSeek, onSkip, onSetSpeed, onNext, onPrev, onSleepTimer)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassPlayerScreen(
    spec: PlayerSpec,
    onClose: () -> Unit, 
    onPlayPause: () -> Unit, 
    onSeek: (Long) -> Unit, 
    onSkip: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onNext: () -> Unit = {},
    onPrev: () -> Unit = {},
    onSleepTimer: () -> Unit = {}
) {
    // Policy: Gestures & Animation State
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    Box(modifier = Modifier.fillMaxSize().offset { IntOffset(0, offsetY.value.roundToInt()) }.pointerInput(Unit) {
        detectDragGestures(
            onDragEnd = { if (offsetY.value > 300f) onClose() else scope.launch { offsetY.animateTo(0f) } },
            onDrag = { change, dragAmount -> 
                if (offsetY.value > 0 || dragAmount.y > 0) {
                    change.consume()
                    scope.launch { offsetY.snapTo((offsetY.value + dragAmount.y).coerceAtLeast(0f)) }
                }
            }
        )
    }) { 
        GlassPlayerMechanism(spec, offsetY.value, onClose, onPlayPause, onSeek, onSkip, onSetSpeed, onNext, onPrev, onSleepTimer)
    }
}

@Composable
fun GlassPlayerMechanism(
    spec: PlayerSpec,
    dragOffset: Float,
    onClose: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkip: (Int) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSleepTimer: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Flux Background
        Box(Modifier.fillMaxSize()) {
            FluxBackground(Modifier.fillMaxSize(), spec.amplitude, Color(spec.dominantColor))
        }

        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // Drag Handle
            Box(Modifier.width(40.dp).height(4.dp).background(Color.White.copy(0.3f), RoundedCornerShape(2.dp)).padding(top = 8.dp).clickable { onClose() })

            Spacer(Modifier.height(32.dp))

            // Artwork & Shadow
            Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                 // Volumetric Shadow
                 Box(Modifier.size(280.dp).graphicsLayer { 
                    translationX = -dragOffset * 0.1f
                    renderEffect = if (Build.VERSION.SDK_INT >= 31) RenderEffect.createBlurEffect(100f, 100f, Shader.TileMode.DECAL).asComposeRenderEffect() else null
                }.background(Color(spec.dominantColor).copy(0.4f), CircleShape))
                
                // Art
                // "Pure Breath": The scale logic. 
                // We use a spring-like response to the amplitude for organic movement.
                val animatedAmplitude by animateFloatAsState(
                    targetValue = spec.amplitude, 
                    animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow),
                    label = "breath"
                )
                val breathScale = 0.98f + (animatedAmplitude * 0.04f)
                
                Box(Modifier.size(280.dp).inertialTilt(15f).scale(breathScale).shadow(24.dp, RoundedCornerShape(32.dp)).clip(RoundedCornerShape(32.dp))) {
                    AsyncImage(spec.imageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    
                    if (spec.sleepTimerSeconds > 0) {
                         Box(Modifier.fillMaxSize().background(Color.Black.copy(0.4f)), contentAlignment = Alignment.TopCenter) {
                             Text(
                                 "${spec.sleepTimerSeconds / 60}:${(spec.sleepTimerSeconds % 60).toString().padStart(2, '0')}", 
                                 color = Color.White, 
                                 fontWeight = FontWeight.Bold,
                                 modifier = Modifier.padding(top = 16.dp)
                             )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Titles
            NebulaText(spec.title, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), speed = spec.speed)
            Spacer(Modifier.height(8.dp))
            Text(spec.artist, color = Color.White.copy(0.7f), style = MaterialTheme.typography.bodyLarge)
            
            Spacer(Modifier.height(32.dp))
            
            // Slider
            Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
                Slider(
                    value = spec.currentMs.toFloat(), 
                    valueRange = 0f..spec.durationMs.toFloat().coerceAtLeast(1f), 
                    onValueChange = { 
                        onSeek(it.toLong())
                        if (it.toLong() % 1000 < 100) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) 
                    }, 
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White, 
                        activeTrackColor = Color(spec.vibrantColor), 
                        inactiveTrackColor = Color.White.copy(0.15f)
                    )
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { 
                Text(formatMs(spec.currentMs), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
                Text(formatMs(spec.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.5f))
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Controls - V2: More Controls
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                // Row 1: Speed, Timer
                IconButton(onClick = { onSetSpeed(if(spec.speed >= 2f) 0.5f else spec.speed + 0.5f); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }) {
                   Text("${spec.speed}x", color = Color.White, fontWeight = FontWeight.Bold)
                }
                
                // Row 2: Transport (Prev, SkipBack, Play, SkipFwd, Next)
                // Let's optimize layout. A single row is crowded.
                // Let's separate Speed/Timer to top or bottom?
                // Or just squeeze them in?
                // User asked for "more controls". 
                // Let's do: [Prev] [Skip-15] [Play] [Skip+30] [Next]
                // And put Speed/Timer elsewhere?
                // Actually, existing design: [Speed] [Skip-15] [Play] [Skip+30] [Timer]
                // Let's change to: [Speed] [Prev] [Play] [Next] [Timer]
                // And maybe tap Play container edges for skip? No, explicit is better.
                // Updated Layout:
                // [Speed] [Prev] [Skip-15] [Play] [Skip+30] [Next] [Timer] -> Too many.
                // Let's drop Skip buttons in favor of standard Prev/Next?
                // No, podcasts need 15/30 skips.
                // Let's do a 2-row layout? Or just squeeze.
            }
            
            // Primary Transport
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                 IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPrev() }) { 
                     Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(32.dp)) 
                 }
                 
                 IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSkip(-15) }) { 
                     Icon(Icons.Rounded.Replay10, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(28.dp)) 
                 }
                 
                 Box(Modifier.size(80.dp).shadow(30.dp, CircleShape, spotColor = if (spec.isPlaying) Color(0xFF00F0FF) else Color.Transparent).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                      MorphingPlayPauseButton(isPlaying = spec.isPlaying, onToggle = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onPlayPause() }, modifier = Modifier.size(32.dp))
                 }
                 
                 IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSkip(30) }) { 
                     Icon(Icons.Rounded.Forward30, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(28.dp)) 
                 }
                 
                 IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onNext() }) { 
                     Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(32.dp)) 
                 }
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Secondary Controls
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                 IconButton(onClick = { onSetSpeed(if(spec.speed >= 2f) 0.5f else spec.speed + 0.5f); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }) {
                   Text("${spec.speed}x", color = Color.White, fontWeight = FontWeight.Bold)
                }
                
                IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSleepTimer() }) { 
                    val alpha = if (spec.sleepTimerSeconds > 0) 1f else 0.5f
                    val tint = if (spec.sleepTimerSeconds > 0) Color(0xFF00F0FF) else Color.White
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Timer, null, tint = tint.copy(alpha))
                        if(spec.sleepTimerSeconds > 0) {
                            Box(Modifier.size(4.dp).background(tint, CircleShape))
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val path = Path()
fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return (1 - fraction) * start + fraction * stop
}

fun formatMs(ms: Long): String { val s = ms / 1000; return String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60) }

@Composable
fun MorphingPlayPauseButton(isPlaying: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val transition = updateTransition(targetState = isPlaying, label = "PlayPause")
    val t by transition.animateFloat(
        transitionSpec = { tween(400, easing = FastOutSlowInEasing) },
        label = "progress"
    ) { if (it) 1f else 0f }

    Canvas(modifier.clickable { 
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onToggle() 
    }) {
        // Morph logic:
        // Pause (1f): Two bars at x=0 and x=2*barWidth
        // Play (0f): Left bar becomes top half of triangle, Right bar becomes bottom half? 
        // Simpler: Just generic shape lerp if using Path, but manual drawing is cleaner for geometric morphs.
        // for geometry morphs. Let's do the "Split Triangle" morph.
        
        // Approach: The "Pause" bars move together and the gap closes, while the 
        // right side scales down to a point.
        
        val width = size.width
        val height = size.height
        val center = height / 2f
        val actualT = t
        
        val barW = width * 0.3f
        
        // Left Part
        path.reset()
        path.moveTo(0f, 0f)
        path.lineTo(lerp(width, barW, actualT), lerp(center, 0f, actualT)) // Point/TopRight
        path.lineTo(lerp(width, barW, actualT), lerp(center, height, actualT)) // Point/BotRight
        path.lineTo(0f, height)
        path.close()
        
        drawPath(path, Color.Black)
        
        // Right Bar (Only visible in Pause, fades out/slides in)
        if (actualT > 0f) {
            drawRect(Color.Black, topLeft = Offset(width - barW, 0f), size = Size(barW, height), alpha = actualT)
        }
    }
}
