package com.example.alakey.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Pure policy used to decide whether ambient motion is worth its cost. */
fun shouldAnimateAmbient(isResumed: Boolean, animatorScale: Float): Boolean =
    isResumed && animatorScale > 0f

@Composable
fun rememberAmbientMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        shouldAnimateAmbient(isResumed = true, animatorScale = scale)
    }
}
