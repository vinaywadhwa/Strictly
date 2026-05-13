package com.vwap.strictly

import android.app.Application
import android.content.Context
import android.content.Intent
import com.vwap.strictly.core.StrictlyConfig
import com.vwap.strictly.core.Violation
import com.vwap.strictly.install.StrictModeInstaller
import com.vwap.strictly.internal.StrictlyRuntime
import com.vwap.strictly.ui.StrictlyActivity
import kotlinx.coroutines.flow.StateFlow

/**
 * Public entry point for Strictly.
 *
 * In typical usage you don't call anything here. The library auto-installs via
 * a [ContentProvider] in its manifest and uses sensible defaults inferred from
 * your `applicationId`.
 *
 * Call [install] explicitly only when you want to override the default
 * configuration. Must be called before any code that could trip StrictMode —
 * realistically that means from `Application.attachBaseContext` or as the
 * first thing in `Application.onCreate`.
 *
 * In release builds, swap to the no-op artifact:
 *
 * ```
 * debugImplementation("com.vwap.strictly:strictly:0.1.0")
 * releaseImplementation("com.vwap.strictly:strictly-noop:0.1.0")
 * ```
 *
 * The no-op artifact has the same class names and method signatures, but every
 * method returns immediately. Production users carry zero overhead.
 */
object Strictly {

    /**
     * Live stream of unique violations seen so far. Keyed by fingerprint.
     * UI surfaces collect this with `collectAsState()`.
     */
    val violations: StateFlow<Map<String, Violation>>
        get() = StrictlyRuntime.requireStore().violations

    /**
     * Install Strictly with a custom configuration. Replaces any auto-install
     * that already happened. Safe to call multiple times; the most recent call
     * wins.
     */
    fun install(application: Application, config: StrictlyConfig = StrictlyConfig()) {
        StrictlyRuntime.install(application, config)
    }

    /**
     * Open the violation detail screen. Call from a debug menu, a developer
     * affordance, or anywhere you want a one-tap entry point.
     */
    fun openDetailScreen(context: Context) {
        val intent = Intent(context, StrictlyActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Capture the current violation set as a "baseline." After this call, the
     * UI flags only violations that originated *after* the mark as new. Useful
     * for triaging a legacy codebase: accept the current backlog, but block
     * regressions in PRs.
     */
    fun markBaseline() {
        StrictlyRuntime.requireStore().markBaselineNow()
    }

    /** Clear the in-memory violation store. */
    fun clear() {
        StrictlyRuntime.requireStore().clear()
    }

    /** Whether the underlying StrictMode listener API is available on this device. */
    val isLiveListeningSupported: Boolean
        get() = android.os.Build.VERSION.SDK_INT >= 28
}
