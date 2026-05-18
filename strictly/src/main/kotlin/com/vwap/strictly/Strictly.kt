package com.vwap.strictly

import android.app.Application
import android.content.Context
import android.content.Intent
import com.vwap.strictly.core.Session
import com.vwap.strictly.core.SessionSummary
import com.vwap.strictly.core.StrictlyConfig
import com.vwap.strictly.core.Violation
import com.vwap.strictly.internal.StrictlyRuntime
import com.vwap.strictly.ui.StrictlyActivity
import kotlinx.coroutines.flow.StateFlow

/**
 * Public entry point for Strictly.
 *
 * In typical usage you don't call anything here. The library auto-installs via
 * a [android.content.ContentProvider] in its manifest and uses sensible defaults inferred from
 * your `applicationId`.
 *
 * Call [install] explicitly only when you want to override the default
 * configuration. Must be called before any code that could trip StrictMode,
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
     * Live stream of unique violations in the **current** session. Keyed by
     * fingerprint. UI surfaces collect this with `collectAsState()`.
     *
     * For archived sessions, use [loadSession] instead.
     */
    val violations: StateFlow<Map<String, Violation>>
        get() = StrictlyRuntime.requireStore().currentViolations

    /**
     * Live stream of session summaries, ordered most-recently-active first.
     * Includes the current session at the top as soon as it has at least one
     * violation. Each summary has counts plus a top type, suitable for a
     * scannable list UI.
     */
    val sessions: StateFlow<List<SessionSummary>>
        get() = StrictlyRuntime.requireStore().sessions

    /** Identifier of the live session — useful when correlating with crash reports. */
    val currentSessionId: String
        get() = StrictlyRuntime.requireStore().currentSessionId

    /**
     * Install Strictly with a custom configuration. Replaces any auto-install
     * that already happened. Safe to call multiple times — the most recent call
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
     * Load a session by id. Returns the live snapshot if the id matches the
     * current session, otherwise reads from disk. Returns null if the id is
     * unknown or the file is corrupt.
     */
    fun loadSession(id: String): Session? =
        StrictlyRuntime.requireStore().loadSession(id)

    /**
     * Clear the **current** session's violations. Persisted archived sessions
     * are untouched. This is what the "Delete" button in the live notification
     * and the live-session screen invokes.
     */
    fun clearCurrent() {
        StrictlyRuntime.requireStore().clearCurrent()
    }

    /** Delete a specific archived session by id. No-op for the live session. */
    fun deleteSession(id: String) {
        StrictlyRuntime.requireStore().deleteSession(id)
    }

    /** Wipe every session (including the live one) from disk and memory. */
    fun wipeAllSessions() {
        StrictlyRuntime.requireStore().wipeAllSessions()
    }

    /** Whether the underlying StrictMode listener API is available on this device. */
    val isLiveListeningSupported: Boolean
        get() = android.os.Build.VERSION.SDK_INT >= 28

    /**
     * Opt-in HTTP debug server. Use [DebugHttp.running] / [DebugHttp.lastError]
     * to drive UI, and [DebugHttp.setEnabled] when the user toggles it.
     */
    object DebugHttp {
        /** Whether the server is actually running right now. */
        val running: StateFlow<Boolean>
            get() = StrictlyRuntime.requireHttp().running

        /**
         * Whether the user *intends* the server to be running, persisted across
         * process restarts. The settings switch binds to this so a bind
         * failure doesn't silently flip the toggle back off behind the user.
         */
        val desired: StateFlow<Boolean>
            get() = StrictlyRuntime.requireHttp().desired

        val lastError: StateFlow<String?>
            get() = StrictlyRuntime.requireHttp().lastError

        /**
         * The device port the server is bound to. Live flow so UI rebinds the
         * displayed `adb forward` command when a collision pushed us off the
         * anchor port. Stable across launches (persisted in
         * [com.vwap.strictly.prefs.StrictlyPrefs.lastBoundPort]).
         */
        val port: StateFlow<Int>
            get() = StrictlyRuntime.requireHttp().port

        val hasSecret: Boolean
            get() = StrictlyRuntime.requireHttp().hasSecret

        fun setEnabled(enabled: Boolean) {
            StrictlyRuntime.requireHttp().setEnabled(enabled)
        }

        /** Re-attempt to start the server after a bind failure. */
        fun retry() {
            StrictlyRuntime.requireHttp().retry()
        }
    }
}
