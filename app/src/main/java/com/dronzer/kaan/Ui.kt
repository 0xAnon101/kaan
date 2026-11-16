package com.dronzer.kaan

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

val Corner = RoundedCornerShape(14.dp)
val CardShape = RoundedCornerShape(22.dp)

fun reducedMotion(ctx: Context): Boolean =
    Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** 3 h 12 min, 45 min, under a minute. */
fun duration(seconds: Long): String {
    val minutes = seconds / 60
    val h = minutes / 60
    val m = minutes % 60
    return when {
        seconds == Long.MAX_VALUE || h >= 24 -> "all day"
        h > 0 && m > 0 -> "$h h $m min"
        h > 0 -> "$h h"
        m > 0 -> "$m min"
        else -> "under a minute"
    }
}

fun percent(dose: Double): Int = (dose * 100).roundToInt()

@Composable
fun Glyph(id: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 24.dp, label: String? = null) {
    Image(
        painter = painterResource(id),
        contentDescription = label,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint)
    )
}

/** Anything tappable. It dips slightly under the finger and springs back. */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: Role = Role.Button,
    label: String? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "press"
    )
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(modifier)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                onClickLabel = label,
                role = role,
                onClick = onClick
            ),
        contentAlignment = contentAlignment,
        content = content
    )
}

@Composable
fun SolidButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    val p = LocalPalette.current
    Pressable(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp).clip(Corner).background(p.accent),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = Type.button, color = p.onAccent, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
    }
}

@Composable
fun OutlineButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    val p = LocalPalette.current
    Pressable(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp).clip(Corner).border(1.5.dp, p.ink, Corner),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = Type.button, color = p.ink, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
    }
}

@Composable
fun TextLink(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = LocalPalette.current.accent) {
    Pressable(onClick = onClick, modifier = modifier.heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) {
        Text(label, style = Type.bodyStrong.copy(textDecoration = TextDecoration.Underline), color = color)
    }
}

@Composable
fun Rule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(LocalPalette.current.line))
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().clip(CardShape).background(LocalPalette.current.surface).padding(20.dp),
        content = content
    )
}

@Composable
fun ScreenTitle(title: String, onBack: (() -> Unit)? = null) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth()) {
        if (onBack != null) {
            Pressable(
                onClick = onBack,
                modifier = Modifier.padding(top = 8.dp).size(48.dp),
                label = "Back",
                contentAlignment = Alignment.CenterStart
            ) {
                Glyph(R.drawable.ic_back, p.ink, label = "Back")
            }
        } else {
            Spacer(Modifier.height(20.dp))
        }
        Text(title, style = Type.title, color = p.ink)
        Spacer(Modifier.height(18.dp))
    }
}

/** A number that counts up to its value when it first appears or changes. */
@Composable
fun CountUp(value: Int, style: TextStyle, color: Color, suffix: String = "", modifier: Modifier = Modifier) {
    val shown by animateIntAsState(value, tween(900, easing = FastOutSlowInEasing), label = "count")
    Text("$shown$suffix", style = style, color = color, modifier = modifier)
}

/** Square-cornered switch. The thumb slides on a spring. */
@Composable
fun Toggle(on: Boolean) {
    val p = LocalPalette.current
    val x by animateDpAsState(if (on) 20.dp else 0.dp, spring(dampingRatio = 0.65f, stiffness = 520f), label = "thumb")
    val track by animateColorAsState(if (on) p.accent else p.line, tween(180), label = "track")
    Box(
        Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(track)
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (on) p.onAccent else p.bg)
        )
    }
}

/** Two or three choices side by side. The chosen one lifts onto the page colour. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Row(
        modifier.fillMaxWidth().clip(Corner).background(p.surface).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) p.bg else p.surface, tween(200), label = "segment")
            Pressable(
                onClick = { onSelect(i) },
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(bg),
                role = Role.Tab,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = if (on) Type.bodyStrong else Type.body,
                    color = if (on) p.ink else p.muted,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepKey("-", "Less", onMinus)
        Text(value, style = Type.heading, color = p.ink, textAlign = TextAlign.Center, modifier = Modifier.width(48.dp))
        StepKey("+", "More", onPlus)
    }
}

@Composable
private fun StepKey(sign: String, label: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Pressable(
        onClick = onClick,
        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).border(1.5.dp, p.line, RoundedCornerShape(12.dp)),
        label = label,
        contentAlignment = Alignment.Center
    ) {
        Text(sign, style = Type.heading, color = p.ink)
    }
}

/** A setting with a title, one line of explanation and a control on the right. */
@Composable
fun SettingRow(
    title: String,
    detail: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit
) {
    val p = LocalPalette.current
    val base = Modifier.fillMaxWidth()
    Column(if (onClick != null) base.clickable(onClick = onClick) else base) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.bodyStrong, color = p.ink)
                Spacer(Modifier.height(2.dp))
                Text(detail, style = Type.small, color = p.muted)
            }
            Spacer(Modifier.width(16.dp))
            trailing()
        }
        Rule()
    }
}

/** A labelled number, used in rows of facts. */
@Composable
fun Fact(value: String, caption: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.ink) {
    Column(modifier) {
        Text(value, style = Type.stat, color = color)
        Spacer(Modifier.height(2.dp))
        Text(caption, style = Type.small, color = LocalPalette.current.muted)
    }
}

/**
 * Lets a block rise into place when its screen opens. Blocks with a higher [order] follow
 * a moment later. The motion runs in the draw phase, so it costs no recomposition.
 */
@Composable
fun Modifier.arrive(order: Int): Modifier {
    val ctx = LocalContext.current
    val still = remember { reducedMotion(ctx) }
    if (still) return this
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(order * 55L)
        shown.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 260f))
    }
    return this.graphicsLayer {
        val v = shown.value
        alpha = v.coerceIn(0f, 1f)
        translationY = (1f - v) * 28.dp.toPx()
    }
}

/**
 * A glyph standing on its own in a square of space. No tinted box behind it.
 * [bg] is kept so callers read the same, and is not drawn.
 */
@Composable
fun IconBlock(icon: Int, bg: Color, tint: Color, modifier: Modifier = Modifier, size: Dp = 48.dp, glyph: Dp = 24.dp) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Glyph(icon, tint, size = if (glyph > size * 0.7f) glyph else size * 0.7f)
    }
}

/** A full colour badge. It turns grey and faint until the badge is earned. */
@Composable
fun BadgeArt(art: Int, earned: Boolean, modifier: Modifier = Modifier, size: Dp = 64.dp) {
    val grey = remember { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    Image(
        painter = painterResource(art),
        contentDescription = null,
        modifier = modifier.size(size).graphicsLayer { alpha = if (earned) 1f else 0.38f },
        colorFilter = if (earned) null else grey
    )
}

/** 1 day 6 h, 5 h, 40 min. For the time left in the trial. */
fun longDuration(ms: Long): String {
    val minutes = ms / 60000L
    val d = minutes / 1440L
    val h = (minutes % 1440L) / 60L
    val m = minutes % 60L
    return when {
        d > 0 && h > 0 -> "$d ${if (d == 1L) "day" else "days"} $h h"
        d > 0 -> "$d ${if (d == 1L) "day" else "days"}"
        h > 0 -> "$h h"
        m > 0 -> "$m min"
        else -> "under a minute"
    }
}
