package com.vwap.strictly.internal

import android.app.Application
import android.content.Context
import com.vwap.strictly.core.StrictlyConfig
import com.vwap.strictly.http.HttpServerController
import com.vwap.strictly.install.StrictModeInstaller
import com.vwap.strictly.notification.LiveNotificationController
import com.vwap.strictly.prefs.StrictlyPrefs
import com.vwap.strictly.shortcut.ShortcutInstaller
import com.vwap.strictly.store.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executor
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

    @Volatile private var store: SessionStore? = null
    @Volatile private var notification: LiveNotificationController? = null
    @Volatile private var shortcut: ShortcutInstaller? = null
    @Volatile private var application: android.app.Application? = null
    @Volatile private var effectiveConfig: StrictlyConfig? = null
    @Volatile private var prefs: StrictlyPrefs? = null
    @Volatile private var http: HttpServerController? = null

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
    private val rawListenerExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Strictly-Listener").apply { isDaemon = true }
    }

    /**
     * ThreadLocal that carries the offending thread's name from the StrictMode
     * policy callback (running on the offending thread, eg "main" or
     * "FinalizerDaemon") into [StrictModeInstaller.handleRawViolation] (running
     * on `Strictly-Listener`). Without this, every violation records its
     * thread as "Strictly-Listener" which is useless dead pixels in the UI.
     */
    internal val offendingThreadName = ThreadLocal<String>()

    /**
     * Wrapping executor that captures the calling thread's name on submission
     * (still on the offending thread), then delegates to [rawListenerExecutor]
     * with a runnable that sets [offendingThreadName] before invoking the
     * original task. Cleared afterwards so we never leak between tasks.
     */
    private val listenerExecutor: Executor = Executor { task ->
        val capturedThreadName = Thread.currentThread().name
        rawListenerExecutor.execute {
            offendingThreadName.set(capturedThreadName)
            try {
                task.run()
            } finally {
                offendingThreadName.remove()
            }
        }
    }

    private val coroutineScope = CoroutineScope(
        SupervisorJob() + rawListenerExecutor.asCoroutineDispatcher(),
    )

    fun install(application: Application, config: StrictlyConfig) {
        // Allow re-install (eg manual override of auto-init). Tear down the old
        // notification so we don't end up with two notifications fighting over
        // the same ID, and the old HTTP server so a fresh bind on the new
        // controller doesn't trip over the previous controller's still-bound
        // socket.
        notification?.dismiss()
        http?.shutdown()

        this.application = application
        val effectiveConfig = config.withInferredAppPackages(application)
        this.effectiveConfig = effectiveConfig
        this.prefs = StrictlyPrefs(application, defaultThemeMode = effectiveConfig.themeMode)
        val store = SessionStore(application, maxStoredSessions = effectiveConfig.maxStoredSessions)
        this.store = store

        val notification = LiveNotificationController(
            context = application,
            scope = coroutineScope,
            store = store,
            prefs = requirePrefs(),
            debounceMillis = effectiveConfig.notificationUpdateDebounceMillis,
            liveUpdates = effectiveConfig.liveNotificationUpdates,
            askForNotificationPermission = effectiveConfig.askForNotificationPermission,
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

        val versionName = runCatching {
            @Suppress("DEPRECATION")
            application.packageManager.getPackageInfo(application.packageName, 0)?.versionName
        }.getOrNull().orEmpty()
        val http = HttpServerController(
            config = effectiveConfig,
            store = store,
            prefs = requirePrefs(),
            packageName = application.packageName,
            versionName = "strictly/$BUILD_VERSION app/$versionName",
        )
        this.http = http
        http.reconcile()

        installed.set(true)
    }

    /** Library version baked at compile time. Surfaced in /v1/health. */
    private const val BUILD_VERSION: String = "0.1.1"

    fun requireStore(): SessionStore =
        store ?: error("Strictly not yet installed. Are you in a release build using the no-op artifact?")

    fun requireApplication(): android.app.Application =
        application ?: error("Strictly not yet installed.")

    fun currentConfig(): StrictlyConfig? = effectiveConfig

    fun requirePrefs(): StrictlyPrefs =
        prefs ?: error("Strictly not yet installed.")

    fun requireHttp(): HttpServerController =
        http ?: error("Strictly not yet installed.")

    /**
     * Notify the live-notification controller that the user has resolved the
     * POST_NOTIFICATIONS permission dialog. Called from
     * [com.vwap.strictly.notification.PermissionRequestActivity]; harmless if
     * Strictly has been uninstalled by the time the dialog closes.
     */
    fun onNotificationPermissionResolved() {
        notification?.onPermissionResolved()
    }

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
