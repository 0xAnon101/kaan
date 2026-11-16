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
    val accent: Color,
    val onAccent: Color,
    val caution: Color,
    val over: Color
) {
    /** Calm below 80 percent of the budget, amber near the limit, red past it. */
    fun state(fraction: Float): Color = when {
        fraction >= 1f -> over
        fraction >= 0.8f -> caution
        else -> accent
    }
}

val LightPalette = Palette(
    bg = Color(0xFFEEF3F5),
    surface = Color(0xFFDFE8EC),
    ink = Color(0xFF0E2230),
    muted = Color(0xFF536976),
    line = Color(0xFFC6D3D9),
    accent = Color(0xFF0B6E8A),
    onAccent = Color(0xFFF2F8FA),
    caution = Color(0xFFB87A06),
    over = Color(0xFFC2412D)
)

val DarkPalette = Palette(
    bg = Color(0xFF0B161C),
    surface = Color(0xFF13242D),
    ink = Color(0xFFE4EDF1),
    muted = Color(0xFF8BA1AD),
    line = Color(0xFF233843),
    accent = Color(0xFF5CC4DE),
    onAccent = Color(0xFF06222B),
    caution = Color(0xFFE3AE45),
    over = Color(0xFFF0745E)
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

object Type {
    val hero = TextStyle(
        fontFamily = Display, fontWeight = FontWeight.ExtraBold,
        fontSize = 104.sp, lineHeight = 104.sp, letterSpacing = (-3).sp
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
    val dark = isSystemInDarkTheme()
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
