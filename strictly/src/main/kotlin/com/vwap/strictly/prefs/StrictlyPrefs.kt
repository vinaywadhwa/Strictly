package com.vwap.strictly.prefs

import android.content.Context
import android.content.SharedPreferences
import com.vwap.strictly.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persistent prefs for Strictly UI state that should survive process death:
 * - Whether the user has opted into the HTTP debug server
 * - (Future) Other user-toggled flags
 *
 * Backed by a single [SharedPreferences] file. Reads on the main thread are
 * OK because StrictMode in the Strictly process is intentionally permissive
 * for our own prefs file (this is a debug-only library) — but every read is
 * wrapped in StrictMode.allowThreadDiskReads anyway to avoid contaminating
 * the consumer app's policy.
 */
internal class StrictlyPrefs(context: Context) {

    private val prefs: SharedPreferences = withRelaxedStrictMode {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _httpEnabled = MutableStateFlow(prefs.getBoolean(KEY_HTTP_ENABLED, false))
    val httpEnabled: StateFlow<Boolean> = _httpEnabled.asStateFlow()

    fun setHttpEnabled(enabled: Boolean) {
        withRelaxedStrictMode { prefs.edit().putBoolean(KEY_HTTP_ENABLED, enabled).apply() }
        _httpEnabled.value = enabled
    }

    private val _notificationPermissionAsked = MutableStateFlow(
        prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, false),
    )
    val notificationPermissionAsked: StateFlow<Boolean> = _notificationPermissionAsked.asStateFlow()

    fun setNotificationPermissionAsked(asked: Boolean) {
        withRelaxedStrictMode {
            prefs.edit().putBoolean(KEY_NOTIFICATION_PERMISSION_ASKED, asked).apply()
        }
        _notificationPermissionAsked.value = asked
    }

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        withRelaxedStrictMode {
            prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        }
        _themeMode.value = mode
    }

    private fun loadThemeMode(): ThemeMode {
        val raw = withRelaxedStrictMode { prefs.getString(KEY_THEME_MODE, null) }
        return ThemeMode.entries.firstOrNull { it.name == raw } ?: ThemeMode.System
    }

    private inline fun <T> withRelaxedStrictMode(block: () -> T): T {
        // Both calls return the *original* policy and then loosen for the
        // single corresponding op. Snapshot once, relax for both ops, then
        // restore the original after the block.
        val original = android.os.StrictMode.allowThreadDiskReads()
        android.os.StrictMode.allowThreadDiskWrites()
        return try {
            block()
        } finally {
            android.os.StrictMode.setThreadPolicy(original)
        }
    }

    private companion object {
        const val PREFS_NAME = "strictly_prefs"
        const val KEY_HTTP_ENABLED = "http_enabled"
        const val KEY_NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
