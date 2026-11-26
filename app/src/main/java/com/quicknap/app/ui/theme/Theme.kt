package com.quicknap.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class QuicknapPalette(
    val bgScreen: Color,
    val bgAwake: Color,
    val bgAsleep: Color,
    val bgSubtle: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val sleepInk: Color,
    val warnBg: Color,
    val warnLine: Color,
    val warnInk: Color,
)

private val LightPalette = QuicknapPalette(
    bgScreen = Color(0xFFF5F7F9),
    bgAwake = Color(0xFFFFFFFF),
    bgAsleep = Color(0xFFE7EDF2),
    bgSubtle = Color(0xFFE2E8EE),
    ink = Color(0xFF111C24),
    muted = Color(0xFF5A6B78),
    line = Color(0xFFD7E0E7),
    accent = Color(0xFF0F766E),
    accentInk = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFCCFBF1),
    sleepInk = Color(0xFF475569),
    warnBg = Color(0xFFFEF3C7),
    warnLine = Color(0xFFF59E0B),
    warnInk = Color(0xFF92400E),
)

private val DarkPalette = QuicknapPalette(
    bgScreen = Color(0xFF11181F),
    bgAwake = Color(0xFF1B2631),
    bgAsleep = Color(0xFF141C24),
    bgSubtle = Color(0xFF202C37),
    ink = Color(0xFFEDF2F7),
    muted = Color(0xFF8FA3B3),
    line = Color(0xFF263442),
    accent = Color(0xFF2DD4BF),
    accentInk = Color(0xFF052623),
    accentSoft = Color(0xFF123836),
    sleepInk = Color(0xFF94A3B8),
    warnBg = Color(0xFF33240D),
    warnLine = Color(0xFFD97706),
    warnInk = Color(0xFFFCD34D),
)

val LocalQuicknapPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun QuicknapTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentInk,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.accent,
            background = palette.bgScreen,
            onBackground = palette.ink,
            surface = palette.bgAwake,
            onSurface = palette.ink,
            surfaceVariant = palette.bgAsleep,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.accentInk,
            primaryContainer = palette.accentSoft,
            onPrimaryContainer = palette.accent,
            background = palette.bgScreen,
            onBackground = palette.ink,
            surface = palette.bgAwake,
            onSurface = palette.ink,
            surfaceVariant = palette.bgAsleep,
            onSurfaceVariant = palette.muted,
            outline = palette.line,
        )
    }

    CompositionLocalProvider(LocalQuicknapPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
