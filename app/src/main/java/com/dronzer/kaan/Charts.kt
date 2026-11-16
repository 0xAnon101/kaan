package com.dronzer.kaan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The hearing budget as a vessel. The water level is the share of the budget used.
 * The surface moves more when the music is louder and settles when it stops.
 */
@Composable
fun Vessel(fill: Float, energy: Float, color: Color, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val still = remember { reducedMotion(ctx) }

    val level by animateFloatAsState(
        targetValue = fill.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 40f),
        label = "level"
    )
    val swell by animateFloatAsState(energy.coerceIn(0f, 1f), tween(700), label = "swell")
    val tint by animateColorAsState(color, tween(600), label = "tint")
    val loop = rememberInfiniteTransition(label = "wave")
    val phase by loop.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing)),
        label = "phase"
    )

    Box(modifier.clip(CardShape).background(p.surface)) {
        Canvas(Modifier.fillMaxSize()) {
            if (level <= 0.002f) return@Canvas
            val w = size.width
            val h = size.height
            val top = h * (1f - level)
            val amp = if (still) 0f else 3.dp.toPx() + 9.dp.toPx() * swell
            val step = 6.dp.toPx()
            val turn = if (still) 0f else phase

            fun surface(shift: Float, alpha: Float) {
                val path = Path()
                path.moveTo(0f, h)
                path.lineTo(0f, top)
                var x = 0f
                while (x <= w + step) {
                    val angle = (x / w) * 3f * PI.toFloat() + turn + shift
                    path.lineTo(x, top + amp * sin(angle))
                    x += step
                }
                path.lineTo(w, h)
                path.close()
                drawPath(path, tint.copy(alpha = alpha))
            }

            surface(1.7f, 0.35f)
            surface(0f, 1f)
        }
    }
}

/**
 * Bars that grow in from left to right. Values run from 0 to 1.
 * An optional limit line marks the daily budget.
 */
@Composable
fun Bars(
    values: List<Float>,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    limit: Float? = null
) {
    val p = LocalPalette.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val grow by animateFloatAsState(if (shown) 1f else 0f, tween(800, easing = FastOutSlowInEasing), label = "grow")

    Canvas(modifier) {
        val n = values.size
        if (n == 0) return@Canvas
        val slot = size.width / n
        val bar = (slot * 0.62f).coerceAtMost(18.dp.toPx())
        val radius = CornerRadius(bar / 3f, bar / 3f)
        val floor = 3.dp.toPx()

        if (limit != null) {
            val y = size.height * (1f - limit)
            drawLine(p.muted, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        values.forEachIndexed { i, v ->
            // Each bar starts a little after the one before it.
            val local = ((grow * (n + 8) - i) / 8f).coerceIn(0f, 1f)
            val height = (floor + (size.height - floor) * v.coerceIn(0f, 1f)) * local
            val x = slot * i + (slot - bar) / 2f
            drawRoundRect(
                color = colors.getOrElse(i) { p.accent },
                topLeft = Offset(x, size.height - height),
                size = Size(bar, height),
                cornerRadius = radius
            )
        }
    }
}

/** A thin horizontal meter for the current loudness. */
@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = 200f), label = "meter")
    val tint by animateColorAsState(color, tween(400), label = "meterTint")
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2.5f, size.height / 2.5f)
        drawRoundRect(p.line, size = size, cornerRadius = r)
        if (shown > 0.01f) drawRoundRect(tint, size = Size(size.width * shown, size.height), cornerRadius = r)
    }
}
