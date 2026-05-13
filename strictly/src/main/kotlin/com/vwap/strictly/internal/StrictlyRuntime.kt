package com.vwap.strictly.internal

import android.app.Application
import android.content.Context
import com.vwap.strictly.core.StrictlyConfig
import com.vwap.strictly.install.StrictModeInstaller
import com.vwap.strictly.notification.LiveNotificationController
import com.vwap.strictly.shortcut.ShortcutInstaller
import com.vwap.strictly.store.ViolationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide runtime singleton. Holds the store, the executor, and the
 * controllers. Lives until process death.
 *
 * Internal: not exposed via the public API; consumers go through [com.vwap.strictly.Strictly].
 */
internal object StrictlyRuntime {

    private val installed = AtomicBoolean(false)

    @Volatile private var store: ViolationStore? = null
    @Volatile private var notification: LiveNotificationController? = null
    @Volatile private var shortcut: ShortcutInstaller? = null

    /**
     * Single-thread executor for the StrictMode penaltyListener callbacks.
     * Reasons for single-thread:
     * - Strict ordering of violation events keeps the de-dupe map consistent
     *   without us having to add a synchronized block in [ViolationStore].
     * - Removes any chance of two listener calls racing to update the
     *   notification, which would cause `cancel`/`notify` interleavings.
     * - The work per event is small (hashing + map update + a debounced
     *   notification post), so one thread is plenty.
     *
     * Named thread for easier debugging in `adb shell ps -T`.
     */
    private val listenerExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Strictly-Listener").apply { isDaemon = true }
    }

    private val coroutineScope = CoroutineScope(
        SupervisorJob() + listenerExecutor.asCoroutineDispatcher(),
    )

    fun install(application: Application, config: StrictlyConfig) {
        // Allow re-install (eg manual override of auto-init). Tear down the old
        // notification so we don't end up with two notifications fighting over
        // the same ID.
        notification?.dismiss()

        val effectiveConfig = config.withInferredAppPackages(application)
        val store = ViolationStore(maxSize = effectiveConfig.maxStoredViolations)
        this.store = store

        val notification = LiveNotificationController(
            context = application,
            scope = coroutineScope,
            store = store,
            debounceMillis = effectiveConfig.notificationUpdateDebounceMillis,
            liveUpdates = effectiveConfig.liveNotificationUpdates,
        )
        this.notification = notification
        notification.start()

        if (effectiveConfig.registerAppShortcut) {
            val shortcut = ShortcutInstaller(application)
            this.shortcut = shortcut
            shortcut.installIfNeeded()
        }

        val installer = StrictModeInstaller(
            config = effectiveConfig,
            listenerExecutor = listenerExecutor,
            onViolation = { violation ->
                store.record(violation)
            },
        )
        installer.install()

        installed.set(true)
    }

    fun requireStore(): ViolationStore =
        store ?: error("Strictly not yet installed. Are you in a release build using the no-op artifact?")

    /**
     * If [StrictlyConfig.appPackages] is empty, infer it from the
     * Application's package name. Two-package fallback: the literal package
     * plus a parent-level prefix, so multi-module apps that use sub-packages
     * still get covered.
     */
    private fun StrictlyConfig.withInferredAppPackages(application: Application): StrictlyConfig {
        if (appPackages.isNotEmpty()) return this
        val pkg = application.packageName.orEmpty()
        val inferred = buildList {
            if (pkg.isNotEmpty()) {
                add("$pkg.")
                // Strip last segment as a wider net (eg `com.acme.app` -> `com.acme.`).
                val lastDot = pkg.lastIndexOf('.')
                if (lastDot > 0) add(pkg.substring(0, lastDot + 1))
            }
        }
        return copy(appPackages = inferred)
    }
}
