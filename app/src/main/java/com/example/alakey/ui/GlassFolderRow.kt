package com.example.alakey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Unsubscribe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun GlassFolderHeader(
    title: String,
    imageUrl: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onUnsubscribe: () -> Unit
) {
    var confirmUnsubscribe by remember { mutableStateOf(false) }
    PrismaticGlass(Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 90.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onToggle).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(imageUrl, null, Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    NebulaText(title, MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text("$count ${if (count == 1) "episode" else "episodes"}", color = Color.White.copy(.6f), style = MaterialTheme.typography.bodySmall)
                }
                Icon(if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, if (isExpanded) "Collapse $title" else "Expand $title", tint = Color.Cyan)
            }
            IconButton(onClick = { confirmUnsubscribe = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Unsubscribe, "Unsubscribe from $title", tint = Color.Red.copy(.8f))
            }
        }
    }
    if (confirmUnsubscribe) {
        AlertDialog(
            onDismissRequest = { confirmUnsubscribe = false },
            title = { Text("Unsubscribe from $title?") },
            text = { Text("This removes the show's episodes from your library.") },
            confirmButton = { TextButton(onClick = { confirmUnsubscribe = false; onUnsubscribe() }) { Text("Unsubscribe") } },
            dismissButton = { TextButton(onClick = { confirmUnsubscribe = false }) { Text("Cancel") } }
        )
    }
}
