package com.example.alakey

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.alakey.ui.AppViewModel
import com.example.alakey.ui.GlassDock
import com.example.alakey.ui.theme.AlakeyTheme

/** Deterministic host used only by debug/instrumentation accessibility checks. */
class ComposeTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AlakeyTheme(darkTheme = true, dynamicColor = false) {
                GlassDock(AppViewModel.Screen.Library, {})
            }
        }
    }
}
