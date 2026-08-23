package com.example.alakey.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.ui.graphics.vector.ImageVector

/** Playback values shared by every projection of the player. */
object PlayerTokens {
    const val SKIP_BACK_SECONDS = 30
    const val SKIP_FORWARD_SECONDS = 30
    const val DISMISS_THRESHOLD_PX = 300f
    const val DISMISS_VELOCITY_PX = 2500f

    fun skipIcon(seconds: Int): ImageVector = when {
        seconds < 0 -> Icons.Rounded.Replay30
        seconds > 0 -> Icons.Rounded.Forward30
        else -> Icons.Rounded.SkipNext
    }

    val previousIcon: ImageVector = Icons.Rounded.SkipPrevious
    val nextIcon: ImageVector = Icons.Rounded.SkipNext
}

fun shouldDismissPlayer(offsetPx: Float, velocityPx: Float): Boolean =
    offsetPx >= PlayerTokens.DISMISS_THRESHOLD_PX || velocityPx >= PlayerTokens.DISMISS_VELOCITY_PX
