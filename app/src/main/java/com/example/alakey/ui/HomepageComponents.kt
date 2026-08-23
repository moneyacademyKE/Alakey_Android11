package com.example.alakey.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.alakey.data.PodcastEntity

// --- Homepage Overhaul Components ---

@Composable
fun GlassDock(
    currentScreen: AppViewModel.Screen,
    onNavigate: (AppViewModel.Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .padding(bottom = 24.dp)
            .height(72.dp)
            .fillMaxWidth(.94f)
            .widthIn(max = 336.dp) // Four compact destinations
    ) {
        // Glass Capsule
        Box(
            Modifier
                .fillMaxSize()
                .shadow(24.dp, CircleShape, spotColor = Color(0xFF00F0FF).copy(0.3f))
                .clip(CircleShape)
                .background(Color.Black.copy(0.6f))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(0.4f), Color.White.copy(0.1f))), CircleShape)
        )
        
        // Content
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DockItem(
                icon = Icons.AutoMirrored.Rounded.LibraryBooks,
                label = "Library",
                isSelected = currentScreen == AppViewModel.Screen.Library,
                onClick = { onNavigate(AppViewModel.Screen.Library) }
            )
            DockItem(
                icon = Icons.Rounded.Inbox,
                label = "Inbox",
                isSelected = currentScreen == AppViewModel.Screen.Inbox,
                onClick = { onNavigate(AppViewModel.Screen.Inbox) }
            )
            DockItem(
                icon = Icons.Rounded.Search,
                label = "Marketplace",
                isSelected = currentScreen == AppViewModel.Screen.Marketplace,
                onClick = { onNavigate(AppViewModel.Screen.Marketplace) }
            )
            DockItem(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Queue",
                isSelected = currentScreen == AppViewModel.Screen.Queue,
                onClick = { onNavigate(AppViewModel.Screen.Queue) }
            )
        }
    }
}

@Composable
private fun DockItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val color by animateColorAsState(targetValue = if (isSelected) Color(0xFF00F0FF) else Color.White.copy(0.4f), label = "color")
    val scale by animateFloatAsState(targetValue = if (isSelected) 1.2f else 1f, label = "scale")
    
    Column(
        modifier = Modifier
            .sizeIn(minWidth = 56.dp, minHeight = 48.dp)
            .semantics {
                contentDescription = label
                role = Role.Tab
                selected = isSelected
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, label, tint = color, modifier = Modifier.size(28.dp).scale(scale))
        Spacer(Modifier.height(4.dp))
        if (isSelected) {
            Box(Modifier.size(4.dp).background(color, CircleShape))
        }
    }
}

@Composable
fun SpotlightHero(
    podcast: PodcastEntity?, // Can be null if nothing playing/featured
    timerSeconds: Int = 0,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTimer: () -> Unit,
    onClick: () -> Unit
) {
    if (podcast == null) return

    Box(
        Modifier
            .fillMaxWidth()
            .height(440.dp) // Slightly taller to accommodate more controls
            .padding(16.dp)
            .clip(RoundedCornerShape(32.dp))
            .clickable { onClick() }
            .semantics { contentDescription = "Open featured episode" }
    ) {
        // Background Image (Darkened)
        AsyncImage(
            model = podcast.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Gradient Scrim
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f), Color.Black))))

        // Timer Badge (Top Right)
        if (timerSeconds > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.6f))
                    .border(1.dp, Color(0xFF00F0FF).copy(0.5f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "${timerSeconds / 60}:${(timerSeconds % 60).toString().padStart(2, '0')}",
                    color = Color(0xFF00F0FF),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Content
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF00F0FF).copy(0.9f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text(if (podcast.lastPlayed > 0) "NOW PLAYING" else "FEATURED", color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(16.dp))
            Text(podcast.episodeTitle, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(podcast.title, color = Color.White.copy(0.7f), style = MaterialTheme.typography.titleSmall)
            
            Spacer(Modifier.height(24.dp))
            
            Row(
                Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // Previous Button
                    IconButton(icon = androidx.compose.material.icons.Icons.Rounded.SkipPrevious, onClick = onPrev)
                    
                    // Play Button
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .clickable { onPlay() }
                            .semantics { contentDescription = "Play featured episode" }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(androidx.compose.material.icons.Icons.Rounded.PlayArrow, "Play featured episode", tint = Color.Black, modifier = Modifier.size(24.dp))
                    }

                    // Next Button
                    IconButton(icon = androidx.compose.material.icons.Icons.Rounded.SkipNext, onClick = onNext)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Timer Button
                    IconButton(
                        icon = androidx.compose.material.icons.Icons.Rounded.Timer, 
                        isSelected = timerSeconds > 0,
                        onClick = onTimer
                    )
                    
                    // Queue Button
                    IconButton(icon = Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = onQueue)
                }
            }
        }
    }
}

@Composable
private fun IconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (isSelected) Color(0xFF00F0FF).copy(0.2f) else Color.White.copy(0.1f))
            .border(1.dp, if (isSelected) Color(0xFF00F0FF).copy(0.4f) else Color.White.copy(0.1f), CircleShape)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .pressScale(interaction)
            .clickable(interactionSource = interaction, indication = LocalIndication.current) { onClick() }
            .semantics { contentDescription = icon.name; role = Role.Button }
            .padding(10.dp)
    ) {
        Icon(icon, contentDescription = icon.name, tint = if (isSelected) Color(0xFF00F0FF) else Color.White, modifier = Modifier.size(22.dp))
    }
}
