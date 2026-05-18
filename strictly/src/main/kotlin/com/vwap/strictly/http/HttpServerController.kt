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
 *
 * Port selection is deterministic per [packageName]: by default we hash the
 * package name into the 8700-8799 band so the same app always lands on the
 * same device port across reinstalls. On bind collision (two apps that hash
 * to the same slot, or some other process holding it) we walk
 * `anchor`, `anchor+1`, `anchor+2`, `anchor+3` and persist the winner so
 * subsequent launches don't drift unless they have to.
 */
internal class HttpServerController(
    private val config: StrictlyConfig,
    private val store: SessionStore,
    private val prefs: StrictlyPrefs,
    private val packageName: String,
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
     * [running] (eg: "Port 8723 is busy").
     */
    val desired: StateFlow<Boolean> = prefs.httpEnabled

    /** The hash-derived anchor port. Stable per [packageName]. */
    private val anchorPort: Int = config.httpDebugPort ?: deriveAnchorPort(packageName)

    /**
     * The port we actually bound on the most recent successful start. Updates
     * live as the server starts; UI reads this for the displayed `adb forward`
     * command so what you copy matches what's listening.
     */
    private val _port = MutableStateFlow(prefs.lastBoundPort() ?: anchorPort)
    val port: StateFlow<Int> = _port.asStateFlow()

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

        // Try the persisted port first (if any AND still within the current
        // anchor's fallback window — if the dev changed httpDebugPort, the old
        // persisted value is stale and shouldn't pull us off the new anchor).
        // Then the deterministic anchor, then a small fallback window. Dedup
        // in case persisted == anchor.
        val attempts = buildList {
            val last = prefs.lastBoundPort()
            if (last != null && last in anchorPort until anchorPort + FALLBACK_WINDOW) {
                add(last)
            }
            for (offset in 0 until FALLBACK_WINDOW) add(anchorPort + offset)
        }.distinct()

        for (candidate in attempts) {
            val s = StrictlyHttpServer(
                port = candidate,
                store = store,
                secret = config.httpDebugSecret,
                versionName = versionName,
            )
            if (s.tryStart()) {
                server = s
                _port.value = candidate
                prefs.setLastBoundPort(candidate)
                _lastError.value = null
                _running.value = true
                return
            }
        }

        // Every candidate was busy. Surface the anchor in the error so the
        // dev can grep for whatever process is squatting it.
        _lastError.value = "Port $anchorPort and the next ${FALLBACK_WINDOW - 1} are all busy. Stop the process holding them or set StrictlyConfig.httpDebugPort to a free port."
        _running.value = false
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

    private companion object {
        /** How many consecutive ports to try after the anchor before giving up. */
        const val FALLBACK_WINDOW = 4
    }
}

/**
 * Map `packageName` to a deterministic port in `[8700, 8799]`. Uses
 * `String.hashCode()` which is contractually stable per the JVM spec, so two
 * machines running this for the same package name always agree on the result.
 *
 * Masks the sign bit (`and Int.MAX_VALUE`) instead of `.absoluteValue` so
 * `Int.MIN_VALUE` doesn't overflow.
 */
private fun deriveAnchorPort(packageName: String): Int =
    8700 + ((packageName.hashCode() and Int.MAX_VALUE) % 100)
