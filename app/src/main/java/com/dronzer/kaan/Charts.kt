package com.dronzer.kaan

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Flat colours for the sky scene at one time of day. No gradients, only layers. */
private class Sky(
    val sky: Color, val body: Color, val face: Color,
    val far: Color, val mid: Color, val near: Color,
    val night: Boolean
)

private fun skyFor(hour: Int, dark: Boolean): Sky {
    val sunUp = hour in 6..18
    if (dark || !sunUp) {
        return Sky(
            sky = Color(0xFF16224A),
            body = if (sunUp) Color(0xFFFFB866) else Color(0xFFF4F1E4),
            face = if (sunUp) Color(0xFF5A3200) else Color(0xFF2A3355),
            far = Color(0xFF24407F), mid = Color(0xFF1A2E63), near = Color(0xFF111E45),
            night = true
        )
    }
    return when {
        hour < 11 -> Sky(
            Color(0xFFFFE2B8), Color(0xFFFFB347), Color(0xFF5A3200),
            Color(0xFFF29A6B), Color(0xFF4A5590), Color(0xFF232E5C), false
        )
        hour < 17 -> Sky(
            Color(0xFFC4E4FF), Color(0xFFFFD45C), Color(0xFF5A3A00),
            Color(0xFF86B6F2), Color(0xFF3F6FD0), Color(0xFF1F3F8F), false
        )
        else -> Sky(
            Color(0xFFFFC7A6), Color(0xFFFF7A45), Color(0xFF4A1A00),
            Color(0xFFC9638A), Color(0xFF5A3F8F), Color(0xFF2A2560), false
        )
    }
}

/** True when text on the scene should be light. */
fun sceneIsDeep(hour: Int, dark: Boolean): Boolean = skyFor(hour, dark).night

/**
 * A small landscape that follows the clock: sunrise, day, sunset, night.
 * The sun or moon wears the mood of the day. [mood] runs from 1, a wide smile, to -1, a frown.
 * Every shape is a flat colour, and every moving value is read in the draw phase.
 */
@Composable
fun Scene(hour: Int, mood: Float, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val still = remember { reducedMotion(ctx) }
    val sky = remember(hour, p.dark) { skyFor(hour, p.dark) }
    val smile by animateFloatAsState(
        mood.coerceIn(-1f, 1f), spring(dampingRatio = 0.45f, stiffness = 140f), label = "smile"
    )
    val loop = rememberInfiniteTransition(label = "scene")
    val drift by loop.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "drift"
    )
    // The eyes close for a moment every few seconds.
    val blink by loop.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                1f at 0
                1f at 3880
                0.12f at 4030
                1f at 4200
            }
        ),
        label = "blink"
    )
    val stars = remember {
        val r = Random(41)
        List(16) { Triple(r.nextFloat(), r.nextFloat() * 0.55f, r.nextFloat()) }
    }

    Box(modifier.clip(CardShape).background(sky.sky)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val turn = if (still) 0f else drift

            if (sky.night) {
                stars.forEach { (sx, sy, seed) ->
                    val glow = 0.45f + 0.55f * (0.5f + 0.5f * sin(turn + seed * 6.28f))
                    val c = Offset(sx * w, sy * h)
                    val arm = (1.5f + 2.5f * seed).dp.toPx()
                    val tone = Color.White.copy(alpha = 0.75f * glow)
                    drawLine(tone, Offset(c.x - arm, c.y), Offset(c.x + arm, c.y), 1.5.dp.toPx(), StrokeCap.Round)
                    drawLine(tone, Offset(c.x, c.y - arm), Offset(c.x, c.y + arm), 1.5.dp.toPx(), StrokeCap.Round)
                }
            }

            // The sun or moon, with the face of the day.
            val r = 34.dp.toPx()
            val cx = w * 0.79f
            val cy = h * 0.36f + (if (still) 0f else 2.dp.toPx() * sin(turn))
            drawCircle(sky.body.copy(alpha = 0.28f), r * 1.32f, Offset(cx, cy))
            drawCircle(sky.body, r, Offset(cx, cy))
            val stroke = 4.dp.toPx()
            val gap = 12.dp.toPx()
            val eye = 8.dp.toPx() * (if (still) 1f else blink)
            val ey = cy - 5.dp.toPx()
            drawLine(sky.face, Offset(cx - gap, ey - eye / 2f), Offset(cx - gap, ey + eye / 2f), stroke, StrokeCap.Round)
            drawLine(sky.face, Offset(cx + gap, ey - eye / 2f), Offset(cx + gap, ey + eye / 2f), stroke, StrokeCap.Round)
            val half = 10.dp.toPx()
            val my = cy + 11.dp.toPx() - smile * 2.dp.toPx()
            val mouth = Path()
            mouth.moveTo(cx - half, my)
            mouth.quadraticTo(cx, my + smile * 11.dp.toPx(), cx + half, my)
            drawPath(mouth, sky.face, style = Stroke(width = stroke, cap = StrokeCap.Round))

            // Three rows of hills, far to near.
            fun hills(color: Color, base: Float, amp: Float, waves: Float, shift: Float) {
                val path = Path()
                path.moveTo(0f, h)
                var x = 0f
                val step = 8.dp.toPx()
                while (x <= w + step) {
                    val a = (x / w) * waves * PI.toFloat() + shift
                    path.lineTo(x, h * base + amp * sin(a) + amp * 0.4f * sin(a * 2.3f + 1f))
                    x += step
                }
                path.lineTo(w, h)
                path.close()
                drawPath(path, color)
            }
            hills(sky.far, 0.66f, 16.dp.toPx(), 2.2f, 0.6f)
            hills(sky.mid, 0.78f, 13.dp.toPx(), 3.1f, 2.4f)
            hills(sky.near, 0.90f, 9.dp.toPx(), 2.6f, 4.1f)
        }
        content()
    }
}

/**
 * The budget as a thick ring. It fills clockwise as the day's budget is used
 * and springs to each new value. [content] sits in the middle.
 */
@Composable
fun Ring(fraction: Float, color: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    val p = LocalPalette.current
    val shown by animateFloatAsState(
        fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.75f, stiffness = 60f), label = "ring"
    )
    val tint by animateColorAsState(color, tween(500), label = "ringTint")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = 16.dp.toPx()
            val inset = width / 2f
            val box = Size(size.minDimension - width, size.minDimension - width)
            val origin = Offset((size.width - box.width) / 2f, (size.height - box.height) / 2f + inset * 0f)
            val track = Stroke(width = width, cap = StrokeCap.Round)
            drawArc(p.line, 135f, 270f, false, origin, box, style = track)
            if (shown > 0.004f) drawArc(tint, 135f, 270f * shown, false, origin, box, style = track)
        }
        content()
    }
}

/** A chunky bar with a glyph at its start, for how loud it is right now. */
@Composable
fun LevelBar(fraction: Float, color: Color, icon: Int, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val shown by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 0.8f, stiffness = 200f), label = "level")
    val tint by animateColorAsState(color, tween(400), label = "levelTint")
    Box(modifier.clip(Corner).background(p.bg), contentAlignment = Alignment.CenterStart) {
        Canvas(Modifier.fillMaxSize()) {
            // Never narrower than the glyph, so the glyph always sits on colour.
            val min = 52.dp.toPx().coerceAtMost(size.width)
            drawRect(tint.copy(alpha = 0.32f), size = Size(min + (size.width - min) * shown, size.height))
        }
        Glyph(icon, p.ink, modifier = Modifier.padding(start = 14.dp))
    }
}

private class Piece(val vx: Float, val vy: Float, val spin: Float, val tone: Int, val long: Boolean, val delay: Float)

/**
 * A burst of paper pieces for good news. It plays once each time [play] changes
 * and draws nothing the rest of the time.
 */
@Composable
fun Confetti(play: Int, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val still = remember { reducedMotion(ctx) }
    val clock = remember { Animatable(1f) }
    val pieces = remember(play) {
        val r = Random(play * 7919 + 13)
        List(54) {
            Piece(
                vx = (r.nextFloat() - 0.5f) * 1.5f,
                vy = -(0.55f + r.nextFloat() * 0.75f),
                spin = (r.nextFloat() - 0.5f) * 4f,
                tone = r.nextInt(5),
                long = r.nextBoolean(),
                delay = r.nextFloat() * 0.12f
            )
        }
    }
    LaunchedEffect(play) {
        if (play > 0 && !still) {
            clock.snapTo(0f)
            clock.animateTo(1f, tween(2200, easing = LinearEasing))
        }
    }
    val tones = listOf(p.joy, p.accent, p.over, p.caution, p.ink)
    Canvas(modifier) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val w = size.width
        val h = size.height
        val unit = 5.dp.toPx()
        pieces.forEach { piece ->
            val s = ((t - piece.delay) * 2.2f).coerceAtLeast(0f)
            if (s <= 0f) return@forEach
            // Thrown up from the lower middle, then pulled down.
            val x = w * 0.5f + piece.vx * s * w * 0.55f
            val y = h * 0.62f + piece.vy * s * h * 0.75f + 0.5f * s * s * h * 0.62f
            val fade = ((1f - t) / 0.25f).coerceIn(0f, 1f)
            val pw = if (piece.long) unit * 2.4f else unit * 1.3f
            val ph = if (piece.long) unit * 0.9f else unit * 1.3f
            rotate(degrees = piece.spin * s * 180f, pivot = Offset(x, y)) {
                drawRoundRect(
                    color = tones[piece.tone].copy(alpha = fade),
                    topLeft = Offset(x - pw / 2f, y - ph / 2f),
                    size = Size(pw, ph),
                    cornerRadius = CornerRadius(unit * 0.3f, unit * 0.3f)
                )
            }
        }
    }
}

/** Short rays that spring out from the middle and fade. Drawn behind a badge when it is earned. */
@Composable
fun Rays(play: Int, color: Color, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val still = remember { reducedMotion(ctx) }
    val out = remember { Animatable(0f) }
    LaunchedEffect(play) {
        if (!still) {
            out.snapTo(0f)
            out.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 90f))
        }
    }
    Canvas(modifier) {
        if (still) return@Canvas
        val v = out.value
        val centre = Offset(size.width / 2f, size.height / 2f)
        val reach = size.minDimension / 2f
        val alpha = (1.25f - v).coerceIn(0f, 1f) * v.coerceIn(0f, 1f)
        for (i in 0 until 12) {
            val angle = (i / 12f) * 2f * PI.toFloat()
            val from = reach * (0.52f + 0.22f * v)
            val to = from + reach * (if (i % 2 == 0) 0.22f else 0.12f)
            drawLine(
                color.copy(alpha = alpha.coerceIn(0f, 1f)),
                Offset(centre.x + from * cos(angle), centre.y + from * sin(angle)),
                Offset(centre.x + to * cos(angle), centre.y + to * sin(angle)),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
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
