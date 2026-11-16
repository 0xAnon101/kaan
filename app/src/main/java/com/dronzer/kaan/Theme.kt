package com.dronzer.kaan

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Funnel Display carries numbers and headings. Funnel Sans carries reading text. */
val Display = FontFamily(
    Font(R.font.funnel_display_medium, FontWeight.Medium),
    Font(R.font.funnel_display_bold, FontWeight.Bold),
    Font(R.font.funnel_display_extrabold, FontWeight.ExtraBold)
)

val Sans = FontFamily(
    Font(R.font.funnel_sans_regular, FontWeight.Normal),
    Font(R.font.funnel_sans_medium, FontWeight.Medium),
    Font(R.font.funnel_sans_semibold, FontWeight.SemiBold)
)

data class Palette(
    val bg: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    /** The one warm brand colour: buttons, links, the active tab. */
    val accent: Color,
    val onAccent: Color,
    /** The budget while all is well. */
    val calm: Color,
    val caution: Color,
    val over: Color,
    /** Sunny yellow for streaks, badges and other good news. */
    val joy: Color,
    val onJoy: Color,
    /** A soft tint of the accent, for blocks that carry good news. */
    val lift: Color,
    /** A soft tint of the over colour, for blocks that carry a warning. */
    val blush: Color,
    val dark: Boolean
) {
    /** Blue below 80 percent of the budget, amber near the limit, red past it. */
    fun state(fraction: Float): Color = when {
        fraction >= 1f -> over
        fraction >= 0.8f -> caution
        else -> calm
    }
}

/** Clean and bright: white blocks on a soft grey page, near-black ink, one orange accent. */
val LightPalette = Palette(
    bg = Color(0xFFF4F5F8),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF12162B),
    muted = Color(0xFF6A6F85),
    line = Color(0xFFE3E5EC),
    accent = Color(0xFFE2531B),
    onAccent = Color(0xFFFFFFFF),
    calm = Color(0xFF2F6FED),
    caution = Color(0xFFB26A00),
    over = Color(0xFFD7263D),
    joy = Color(0xFFFFC533),
    onJoy = Color(0xFF2E2200),
    lift = Color(0xFFFFE9DE),
    blush = Color(0xFFFCE1E4),
    dark = false
)

/** Night blue, soft white ink, the same orange accent turned up a little. */
val DarkPalette = Palette(
    bg = Color(0xFF0B1020),
    surface = Color(0xFF151B30),
    ink = Color(0xFFF2F4FA),
    muted = Color(0xFF969DB5),
    line = Color(0xFF232A44),
    accent = Color(0xFFFF8A4C),
    onAccent = Color(0xFF2A0F00),
    calm = Color(0xFF6AA8FF),
    caution = Color(0xFFFFC24D),
    over = Color(0xFFFF6B78),
    joy = Color(0xFFFFD45C),
    onJoy = Color(0xFF2E2200),
    lift = Color(0xFF3A2418),
    blush = Color(0xFF47202A),
    dark = true
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object Type {
    val hero = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.ExtraBold,
        fontSize = 104.sp, lineHeight = 104.sp, letterSpacing = (-3).sp
    )
    val big = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.ExtraBold,
        fontSize = 56.sp, lineHeight = 58.sp, letterSpacing = (-1.5).sp
    )
    val title = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp
    )
    val stat = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 28.sp
    )
    val heading = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.Medium,
        fontSize = 20.sp, lineHeight = 26.sp
    )
    val body = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp
    )
    val bodyStrong = body.copy(fontWeight = FontWeight.SemiBold)
    val small = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp
    )
    val label = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp
    )
    val button = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp
    )
}

/** Things that move through space use a spring, as current Android motion guidance advises. */
fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.82f, stiffness = 380f)

@Composable
fun KaanTheme(content: @Composable () -> Unit) {
    // 0 follows the phone, 1 is light, 2 is dark. Set under Appearance in Settings.
    val dark = when (Live.theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.ink, surface = p.bg, onSurface = p.ink)
    } else {
        lightColorScheme(primary = p.accent, onPrimary = p.onAccent, background = p.bg, onBackground = p.ink, surface = p.bg, onSurface = p.ink)
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
