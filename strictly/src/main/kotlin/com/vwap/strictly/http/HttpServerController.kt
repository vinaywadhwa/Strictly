package com.vwap.strictly.http

import com.vwap.strictly.core.StrictlyConfig
import com.vwap.strictly.prefs.StrictlyPrefs
import com.vwap.strictly.store.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Top-level orchestration for the opt-in HTTP debug server.
 *
 * Holds a single [StrictlyHttpServer] instance. Exposes the running state as
 * a [StateFlow] so the settings sheet's toggle can react. Auto-starts when
 * either:
 * - The user has opted in via [StrictlyPrefs.httpEnabled], or
 * - The build sets [StrictlyConfig.httpDebugAutoStart] = true (for CI).
 */
internal class HttpServerController(
    private val config: StrictlyConfig,
    private val store: SessionStore,
    private val prefs: StrictlyPrefs,
    private val versionName: String,
) {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /**
     * The user's persisted *intent* to run the server, independent of whether
     * it actually started. The settings sheet binds its switch to this so the
     * toggle reflects "I asked for this on" even when a bind failure left the
     * server down. The subtitle then shows the gap between desired and
     * [running] (eg: "Port 8765 is busy").
     */
    val desired: StateFlow<Boolean> = prefs.httpEnabled

    val port: Int get() = config.httpDebugPort
    val hasSecret: Boolean get() = !config.httpDebugSecret.isNullOrEmpty()

    @Volatile
    private var server: StrictlyHttpServer? = null

    /**
     * Reconcile actual server state with the desired state expressed by the
     * prefs flag (or autostart). Idempotent. Call this at install and any
     * time the user toggles the pref.
     */
    fun reconcile() {
        val want = desired.value || config.httpDebugAutoStart
        if (want) ensureStarted() else ensureStopped()
    }

    fun setEnabled(enabled: Boolean) {
        prefs.setHttpEnabled(enabled)
        reconcile()
    }

    /**
     * Force a retry without changing the persisted pref. Used by the "Retry"
     * affordance the settings sheet exposes when [lastError] is set.
     */
    fun retry() {
        ensureStopped()
        _lastError.value = null
        reconcile()
    }

    /**
     * Release the bound socket regardless of pref state. Used by
     * [com.vwap.strictly.internal.StrictlyRuntime.install] to dispose the
     * previous controller before constructing a new one on re-install, so the
     * old server doesn't keep holding the port while the new controller's
     * bind attempt fails with a spurious "Port busy" error.
     */
    fun shutdown() {
        ensureStopped()
    }

    private fun ensureStarted() {
        if (server != null) return
        val s = StrictlyHttpServer(
            port = config.httpDebugPort,
            store = store,
            secret = config.httpDebugSecret,
            versionName = versionName,
        )
        if (s.tryStart()) {
            server = s
            _lastError.value = null
            _running.value = true
        } else {
            _lastError.value = "Port ${config.httpDebugPort} is busy. Change Strictly's httpDebugPort or stop the process holding it."
            _running.value = false
        }
    }

    private fun ensureStopped() {
        val s = server
        if (s != null) {
            runCatching { s.stop() }
            server = null
        }
        _running.value = false
        // Stopping clears any prior bind error: the user has expressed a new
        // intent (off) so the old failure is no longer relevant.
        _lastError.value = null
    }
}
