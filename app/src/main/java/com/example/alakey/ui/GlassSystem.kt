package com.example.alakey.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alakey.ui.theme.LIQUID_PLASMA_SRC
import android.graphics.RuntimeShader
import android.os.Build

@Composable
fun FluxBackground(modifier: Modifier = Modifier, amplitude: Float = 1f, color: Color = Color.Cyan) {
    val motionEnabled = rememberAmbientMotionEnabled()
    if (Build.VERSION.SDK_INT < 33 || !motionEnabled) {
        Box(modifier.fillMaxSize().background(Brush.radialGradient(listOf(color.copy(.2f), Color(0xFF020024)))))
        return
    }
    val transition = rememberInfiniteTransition(label = "background_time")
    val time by transition.animateFloat(0f, 30f, infiniteRepeatable(tween(45_000, easing = LinearEasing)), label = "time")
    val shader = remember { RuntimeShader(LIQUID_PLASMA_SRC) }
    Canvas(modifier.fillMaxSize()) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", time * (1f + amplitude * 2f))
        drawRect(ShaderBrush(shader))
        drawRect(Brush.radialGradient(listOf(color.copy(.1f + amplitude * .2f), Color.Transparent), radius = size.maxDimension), blendMode = BlendMode.Screen)
    }
}

fun Modifier.pressScale(interactionSource: MutableInteractionSource, targetScale: Float = .95f) = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) targetScale else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press_scale")
    this.scale(scale)
}

fun Modifier.glassShimmer(enabled: Boolean = false) = if (!enabled) this else composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(-1f, 2f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "x")
    drawWithCache {
        val brush = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(.08f), Color.Transparent), Offset(size.width * offset, 0f), Offset(size.width * offset + 100f, size.height))
        onDrawWithContent { drawContent(); drawRect(brush) }
    }
}

@Composable
fun PrismaticGlass(modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(24.dp), content: @Composable BoxScope.() -> Unit) {
    val density = LocalDensity.current
    Box(modifier.clip(shape).background(Color.White.copy(.05f)).drawWithCache {
        val path = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(shape.topStart.toPx(size, density)))) }
        val spectrum = Brush.sweepGradient(listOf(Color.Cyan.copy(.6f), Color(0xFFBD00FF).copy(.6f), Color.Yellow.copy(.4f), Color.Cyan.copy(.6f)))
        onDrawWithContent { drawContent(); drawPath(path, spectrum, style = Stroke(1.2.dp.toPx())) }
    }, content = content)
}

/** Determinate progress ring — the shared "state you can watch" primitive (download fill, timer drain). */
@Composable
fun ProgressRing(fraction: Float, modifier: Modifier = Modifier, color: Color = Color.Cyan, trackAlpha: Float = .2f, strokeWidth: Float = 3f) {
    val sweep = (fraction.coerceIn(0f, 1f)) * 360f
    Canvas(modifier) {
        val stroke = strokeWidth * density
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawCircle(color.copy(alpha = trackAlpha), radius = (size.minDimension - stroke) / 2, center = center, style = Stroke(stroke / 2))
        if (sweep > 0f) drawArc(color, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun NebulaText(text: String, style: androidx.compose.ui.text.TextStyle, modifier: Modifier = Modifier, glowColor: Color = Color.Cyan, speed: Float = 1f) {
    val weight = when { speed < .9f -> FontWeight.Light; speed > 1.4f -> FontWeight.Black; speed > 1.1f -> FontWeight.Bold; else -> style.fontWeight ?: FontWeight.Normal }
    val spacing = when { speed < .9f -> 2.sp; speed > 1.4f -> (-.5).sp; else -> style.letterSpacing }
    Box(modifier) {
        Text(text, style = style.copy(fontWeight = weight, letterSpacing = spacing), color = glowColor.copy(.45f), modifier = Modifier.clearAndSetSemantics { })
        Text(text, style = style.copy(fontWeight = weight, letterSpacing = spacing), color = Color.White.copy(.98f))
    }
}
