package com.example.alakey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog

@Composable
fun DebugOverlay(
    historySize: Int,
    onTimeTravel: (Int) -> Unit,
    logs: List<com.example.alakey.data.EventLogEntity>
) {
    if (historySize < 2) return

    var sliderValue by remember { mutableFloatStateOf(0f) }
    var showLogs by remember { mutableStateOf(false) }

    // Sync slider to history size updates
    LaunchedEffect(historySize) {
        sliderValue = historySize.toFloat() - 1f
    }

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(100f) // Always on top
            .padding(bottom = 100.dp), // Avoid nav bar
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.7f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Epochal Time Travel: ${sliderValue.toInt()} / ${historySize - 1}",
                    color = Color.Green,
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        sliderValue = it
                        onTimeTravel(it.toInt())
                    },
                    valueRange = 0f..(historySize - 1).toFloat().coerceAtLeast(0f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Green,
                        activeTrackColor = Color.Green.copy(0.5f)
                    )
                )

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                     TextButton(onClick = { showLogs = !showLogs }) {
                         Text("Toggle Logs", color = Color.White)
                     }
                }
            }
        }
    }

    if (showLogs) {
        Dialog(onDismissRequest = { showLogs = false }) {
             Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(0.9f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp).fillMaxWidth().fillMaxHeight(0.8f)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("System Logs", style = MaterialTheme.typography.titleMedium, color = Color.Cyan)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn {
                        items(logs) { log ->
                             Row(Modifier.padding(vertical = 4.dp)) {
                                 val color = when(log.status) {
                                     "FAILED" -> Color.Red
                                     "COMPLETED" -> Color.Green
                                     else -> Color.Yellow
                                 }
                                 Text("[${log.status}]", color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(80.dp))
                                 Text(log.payload, color = Color.White, style = MaterialTheme.typography.bodySmall)
                             }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { showLogs = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }
    }
}
