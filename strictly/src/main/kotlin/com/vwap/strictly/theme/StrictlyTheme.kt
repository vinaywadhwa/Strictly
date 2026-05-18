package com.vwap.strictly.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * User-overrideable theme selection. Defaults to [System] which delegates to
 * `isSystemInDarkTheme()`. Persisted via [com.vwap.strictly.prefs.StrictlyPrefs].
 *
 * Public so devs can hard-code a preference via
 * [com.vwap.strictly.core.StrictlyConfig.themeMode] — useful because
 * SharedPreferences are wiped on uninstall, but a config-level default lives
 * in the app's source and survives every reinstall.
 */
enum class ThemeMode(internal val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

/**
 * Strictly's brand: neutral graphite that adapts to the active theme (near-black
 * in light mode, near-white in dark mode), with amber for repeat offenders and
 * red for critical violations. Neutral over vivid by design — Strictly is a dev
 * tool that lives next to your IDE, not a consumer app fighting for attention.
 *
 * [Primary] and [OnPrimary] are Composable getters that read from the active
 * MaterialTheme, so they adapt automatically without each call site picking a
 * variant. Severity colors ([Amber], [Red]) are mode-invariant on purpose:
 * "this is bad" should read the same in light and dark.
 */
internal object StrictlyBrand {
    /** Mode-adaptive: near-black in light mode, near-white in dark mode. */
    val Primary: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.primary

    /** Mode-adaptive contrast color for content rendered on top of [Primary]. */
    val OnPrimary: Color
        @Composable @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.onPrimary

    // Severity tones (intentionally mode-invariant).
    val Amber = Color(0xFFF59E0B)
    val Red = Color(0xFFE11D48)

    // Surface tokens consumed by the theme construction below.
    val Surface = Color(0xFFFAFAFA)
    val OnSurface = Color(0xFF111111)
    val SurfaceDark = Color(0xFF0F0F12)
    val OnSurfaceDark = Color(0xFFF5F5F7)
    val Outline = Color(0xFFE4E4E7)
    val OutlineDark = Color(0xFF27272A)
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF18181B),          // zinc-900
    onPrimary = Color(0xFFFAFAFA),        // zinc-50
    primaryContainer = Color(0xFFF4F4F5), // zinc-100
    onPrimaryContainer = Color(0xFF27272A), // zinc-800
    surface = StrictlyBrand.Surface,
    onSurface = StrictlyBrand.OnSurface,
    surfaceVariant = Color(0xFFF4F4F5),
    onSurfaceVariant = Color(0xFF52525B),
    outline = StrictlyBrand.Outline,
    error = StrictlyBrand.Red,
    tertiary = StrictlyBrand.Amber,
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFE4E4E7),          // zinc-200
    onPrimary = Color(0xFF18181B),        // zinc-900
    primaryContainer = Color(0xFF27272A), // zinc-800
    onPrimaryContainer = Color(0xFFF4F4F5), // zinc-100
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
