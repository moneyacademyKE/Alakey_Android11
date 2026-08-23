package com.example.alakey.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.math.roundToInt

private enum class PendingEpisodeAction { ArchiveOlder, DeleteDownload }

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
    var pending by remember { mutableStateOf<PendingEpisodeAction?>(null) }
    PrismaticGlass(Modifier.fillMaxWidth().padding(vertical = 4.dp).heightIn(min = 88.dp).glassShimmer(spec.isSyncing)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).combinedClickable(onClick = onClick, onLongClick = { showMenu = true }).semantics { contentDescription = "Open ${spec.title}" }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(spec.imageUrl, null, Modifier.size(60.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    NebulaText(spec.title, MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    if (spec.subtitle.isNotBlank()) Text(spec.subtitle, color = Color.White.copy(.62f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (spec.progress > 0f) StatusPill(if (spec.progress >= .95f) "Played" else "${(spec.progress * 100).roundToInt()}%", Color(0xFFFFB300))
                        if (spec.remainingMs > 0) StatusPill("${(spec.remainingMs + 59_999) / 60_000} min left", Color(0xFFFFB300))
                        if (spec.isInQueue) StatusPill("Queued", Color(0xFFBD00FF))
                        if (spec.isDownloaded) StatusPill("Offline", Color(0xFF00E676))
                        when (spec.downloadOp) {
                            AsyncOp.InFlight -> StatusPill("Working", Color.Cyan)
                            is AsyncOp.Progress -> StatusPill(if (spec.downloadOp.totalBytes != null && spec.downloadOp.totalBytes > 0) "${(100 * spec.downloadOp.completedBytes / spec.downloadOp.totalBytes).coerceIn(0, 100)}%" else "Downloading", Color.Cyan)
                            is AsyncOp.Failed -> StatusPill("Retry", MaterialTheme.colorScheme.error)
                            else -> Unit
                        }
                    }
                }
                DropdownMenu(showMenu, { showMenu = false }, modifier = Modifier.background(Color.Black.copy(.9f))) {
                    DropdownMenuItem({ Text(if (spec.isInQueue) "Remove from queue" else "Add to queue") }, { onAddToQueue(); showMenu = false })
                    DropdownMenuItem({ Text("Play next") }, { onPlayNext(); showMenu = false })
                    DropdownMenuItem({ Text("Mark played") }, { onMarkPlayed(); showMenu = false })
                    DropdownMenuItem({ Text("Archive all older") }, { pending = PendingEpisodeAction.ArchiveOlder; showMenu = false })
                    if (spec.isDownloaded) DropdownMenuItem({ Text("Delete download", color = MaterialTheme.colorScheme.error) }, { pending = PendingEpisodeAction.DeleteDownload; showMenu = false })
                }
            }
            EpisodeTrailingAction(spec, onClick, onDownload, onAddToQueue)
        }
    }
    pending?.let { action ->
        val deleting = action == PendingEpisodeAction.DeleteDownload
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (deleting) "Delete this download?" else "Archive all older episodes?") },
            text = { Text(if (deleting) "The episode remains in your library and can be downloaded again." else "All older episodes in this show will be marked played.") },
            confirmButton = { TextButton(onClick = { pending = null; if (deleting) onDeleteDownload() else onArchiveOlder() }) { Text(if (deleting) "Delete" else "Archive") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EpisodeTrailingAction(spec: PodcastRowSpec, onClick: () -> Unit, onDownload: () -> Unit, onAddToQueue: () -> Unit) {
    when {
        spec.downloadOp is AsyncOp.Progress && spec.downloadOp.totalBytes != null && spec.downloadOp.totalBytes > 0 -> {
            val fraction = (spec.downloadOp.completedBytes.toFloat() / spec.downloadOp.totalBytes).coerceIn(0f, 1f)
            Box(Modifier.padding(12.dp).size(40.dp), Alignment.Center) {
                ProgressRing(fraction = fraction, color = Color.Cyan, modifier = Modifier.size(32.dp), strokeWidth = 2.5f)
                Text("${(fraction * 100).roundToInt()}", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.semantics { contentDescription = "Downloading, ${(fraction * 100).roundToInt()} percent" })
            }
        }
        spec.downloadOp is AsyncOp.InFlight || spec.downloadOp is AsyncOp.Progress || spec.isSyncing -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.padding(12.dp).size(24.dp))
        spec.downloadOp is AsyncOp.Failed -> IconButton(onClick = onDownload, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.ErrorOutline, "Retry download", tint = MaterialTheme.colorScheme.error) }
        spec.isDownloaded -> Icon(Icons.Rounded.CheckCircle, "Downloaded", tint = Color.Green.copy(.75f), modifier = Modifier.padding(12.dp).size(24.dp))
        spec.isInQueue -> IconButton(onClick = onAddToQueue, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.PlaylistRemove, "Remove from queue", tint = Color(0xFFBD00FF)) }
        spec.progress > 0f -> IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.PlayCircle, "Resume episode", tint = Color(0xFFFFB300)) }
        else -> IconButton(onClick = onDownload, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.CloudDownload, "Download episode", tint = Color.White) }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(Modifier.clip(CircleShape).background(color.copy(.16f)).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}
