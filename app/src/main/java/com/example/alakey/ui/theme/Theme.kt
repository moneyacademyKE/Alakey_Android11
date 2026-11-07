package com.example.alakey.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * M57: the app is dark by design — both AlakeyTheme call sites pinned
 * (darkTheme = true, dynamicColor = false), so the light scheme, the
 * dynamic-colour branch and both parameters were 3-of-4 dead branches
 * compiled into every build. One scheme, one cyan: the token previously
 * disagreed with the literals the paint layer actually used, so primary is
 * the cyan that was actually painted (0xFF00F0FF) and the paint layer
 * migrates to MaterialTheme.colorScheme.primary — one accent, one owner.
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00F0FF),
    secondary = Color(0xFFBD00FF),
    tertiary = Color(0xFFFFB300),
    background = Color.Black,
    surface = Color(0xFF101018),
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun AlakeyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
