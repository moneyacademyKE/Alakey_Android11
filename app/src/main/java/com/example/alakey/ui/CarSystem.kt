package com.example.alakey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CarModeScreen(
    spec: PlayerSpec?,
    onTogglePlay: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBack: () -> Unit,
    onExit: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (spec == null) {
            Text("No media. Choose an episode before entering car mode.", color = Color.LightGray, fontSize = 24.sp)
        } else {
            Text(spec.title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(spec.artist, color = Color.LightGray, fontSize = 20.sp, maxLines = 1, modifier = Modifier.padding(bottom = 48.dp))
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly, Alignment.CenterVertically) {
            CarControl(Icons.Rounded.Replay30, "Skip back 30 seconds", spec != null, onSkipBack, 80.dp)
            CarControl(if (spec?.isPlaying == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (spec?.isPlaying == true) "Pause" else "Play", spec != null, onTogglePlay, 120.dp, Color(0xFFFF6A00))
            CarControl(Icons.Rounded.Forward30, "Skip forward 30 seconds", spec != null, onSkipForward, 80.dp)
        }
        Spacer(Modifier.height(48.dp))
        Box(Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(16.dp)).background(Color.DarkGray).clickable(onClick = onExit).semantics { role = Role.Button }, Alignment.Center) {
            Text("EXIT CAR MODE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun CarControl(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp, color: Color = Color.DarkGray) {
    val modifier = Modifier.size(size).clip(CircleShape).background(if (enabled) color else Color.DarkGray.copy(.4f))
        .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
        .semantics { role = Role.Button; if (!enabled) disabled() }
    Box(modifier, Alignment.Center) { Icon(icon, label, tint = if (enabled) Color.White else Color.Gray, modifier = Modifier.size(size * .5f)) }
}
