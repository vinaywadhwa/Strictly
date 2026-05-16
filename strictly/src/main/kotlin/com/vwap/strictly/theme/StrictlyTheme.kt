package com.vwap.strictly.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * User-overrideable theme selection. Defaults to [System] which delegates to
 * `isSystemInDarkTheme()`. Persisted via [com.vwap.strictly.prefs.StrictlyPrefs].
 */
internal enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

/**
 * Strictly's brand: a confident indigo on near-black, with amber for repeat
 * offenders and red for critical violations. Distinct from LeakCanary pink
 * and Chucker blue so devs can recognize Strictly at a glance.
 */
internal object StrictlyBrand {
    val Primary = Color(0xFF7C3AED)        // indigo-violet
    val PrimaryDim = Color(0xFF5B21B6)
    val OnPrimary = Color(0xFFFFFFFF)
    val Surface = Color(0xFFFAFAFA)
    val OnSurface = Color(0xFF111111)
    val SurfaceDark = Color(0xFF0F0F12)
    val OnSurfaceDark = Color(0xFFF5F5F7)
    val Amber = Color(0xFFF59E0B)
    val Red = Color(0xFFEF4444)
    val Outline = Color(0xFFE4E4E7)
    val OutlineDark = Color(0xFF27272A)
}

private val LightScheme = lightColorScheme(
    primary = StrictlyBrand.Primary,
    onPrimary = StrictlyBrand.OnPrimary,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    surface = StrictlyBrand.Surface,
    onSurface = StrictlyBrand.OnSurface,
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF52525B),
    outline = StrictlyBrand.Outline,
    error = StrictlyBrand.Red,
    tertiary = StrictlyBrand.Amber,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = Color(0xFFEDE9FE),
    surface = StrictlyBrand.SurfaceDark,
    onSurface = StrictlyBrand.OnSurfaceDark,
    surfaceVariant = Color(0xFF1F1F23),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = StrictlyBrand.OutlineDark,
    error = Color(0xFFFCA5A5),
    tertiary = Color(0xFFFCD34D),
)

@Composable
internal fun StrictlyTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content,
    )
}
