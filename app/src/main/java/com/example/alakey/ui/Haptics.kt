package com.example.alakey.ui

import android.view.HapticFeedbackConstants
import android.view.View

/** Central haptic vocabulary: confirm for actions, tick for quantized scrubbing. */
object Haptics {
    fun confirm(view: View) = view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    fun tick(view: View) = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}
