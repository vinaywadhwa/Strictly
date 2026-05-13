package com.vwap.strictly.install

import android.os.Build
import android.os.StrictMode
import android.util.Log
import androidx.annotation.RequiresApi
import com.vwap.strictly.core.Fingerprint
import com.vwap.strictly.core.StackFrame
import com.vwap.strictly.core.StrictlyConfig
import com.vwap.strictly.core.Violation
import com.vwap.strictly.core.ViolationType
import java.util.concurrent.Executor

/**
 * Installs StrictMode policies wired to our violation listener.
 *
 * Why this lives in its own file:
 * - It's the one place that touches `StrictMode.*` API surface, so we can guard
 *   the API 28+ requirement at the boundary.
 * - The mapping from `android.os.strictmode.Violation` subclasses to our
 *   [ViolationType] is also API-version-sensitive; centralizing it here means
 *   adding a new violation type (when Google ships one) is a one-file change.
 */
internal class StrictModeInstaller(
    private val config: StrictlyConfig,
    private val listenerExecutor: Executor,
    private val onViolation: (Violation) -> Unit,
) {

    fun install() {
        if (!config.enabled) return

        if (Build.VERSION.SDK_INT >= 28) {
            installApi28Plus()
        } else {
            // On API < 28 there's no programmatic listener. We still install a
            // sensible policy so violations land in Logcat, but the live UI
            // (notification + detail screen) will simply have nothing to show.
            installLegacy()
            Log.i(
                TAG,
                "Strictly installed in legacy mode (API ${Build.VERSION.SDK_INT}); " +
                    "live UI requires API 28+. Violations will appear in Logcat only.",
            )
        }
    }

    private fun installLegacy() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .applyDetectorsForLegacy()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .applyVmDetectorsForLegacy()
                .penaltyLog()
                .build(),
        )
    }

    @RequiresApi(28)
    private fun installApi28Plus() {
        val threadListener = StrictMode.OnThreadViolationListener { v ->
            handleRawViolation(v, isThreadViolation = true)
        }
        val vmListener = StrictMode.OnVmViolationListener { v ->
            handleRawViolation(v, isThreadViolation = false)
        }

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .applyDetectorsForLegacy()
                .penaltyLog()
                .penaltyListener(listenerExecutor, threadListener)
                .build(),
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .applyVmDetectorsForLegacy()
                .penaltyLog()
                .penaltyListener(listenerExecutor, vmListener)
                .build(),
        )
    }

    /**
     * Map a raw `android.os.strictmode.Violation` into our domain model and
     * push it to the store.
     *
     * Self-instrumentation guard: we wrap this in a `permitAll()` block so any
     * disk reads we do (eg loading a resource bundle for the notification text)
     * don't themselves fire violations and recurse. Important for not melting
     * the device when the listener is hot.
     */
    private fun handleRawViolation(raw: Throwable, isThreadViolation: Boolean) {
        val previousThreadPolicy = StrictMode.allowThreadDiskReads()
        try {
            val type = classifyViolation(raw, isThreadViolation)
            if (type !in config.detectedTypes) return

            val frames = raw.stackTrace.map { it.toStackFrame() }
            val firstAppFrame = frames.firstOrNull { f -> isAppFrame(f.className) }

            if (firstAppFrame != null && config.ignoredPackages.any { firstAppFrame.className.startsWith(it) }) {
                return
            }

            val fingerprint = Fingerprint.compute(type, frames, config.appPackages)
            val now = System.currentTimeMillis()
            val violation = Violation(
                fingerprint = fingerprint,
                type = type,
                message = raw.message.orEmpty(),
                stackTrace = frames,
                firstAppFrame = firstAppFrame,
                firstOccurrenceAtMillis = now,
                lastOccurrenceAtMillis = now,
                occurrenceCount = 1,
                threadName = Thread.currentThread().name,
                processName = "main", // refined by caller via process tag if needed
            )
            onViolation(violation)
        } catch (t: Throwable) {
            // Never let our listener crash the host app. A violation in the
            // violation-handler is the worst possible debug-only failure mode.
            Log.w(TAG, "Failed to handle StrictMode violation", t)
        } finally {
            StrictMode.setThreadPolicy(previousThreadPolicy)
        }
    }

    private fun isAppFrame(className: String): Boolean {
        return config.appPackages.any { className.startsWith(it) }
    }

    private fun StackTraceElement.toStackFrame() = StackFrame(
        className = className ?: "<unknown>",
        methodName = methodName ?: "<unknown>",
        fileName = fileName,
        lineNumber = lineNumber,
    )

    /**
     * Classify a raw violation by its concrete class name. We use reflection
     * on the simple class name rather than `is` checks because:
     * - The concrete violation classes (DiskReadViolation, NetworkViolation, ...)
     *   live in `android.os.strictmode.*` which is API 28+ and the set grows
     *   over time. We don't want a compile-time dependency on every subtype.
     * - String matching on simple names is cheap and survives Google adding new
     *   types without us needing a release.
     */
    private fun classifyViolation(raw: Throwable, isThreadViolation: Boolean): ViolationType {
        val simpleName = raw.javaClass.simpleName
        return when (simpleName) {
            "DiskReadViolation" -> ViolationType.DiskRead
            "DiskWriteViolation" -> ViolationType.DiskWrite
            "NetworkViolation" -> ViolationType.Network
            "CustomViolation" -> ViolationType.CustomSlowCall
            "ResourceMismatchViolation" -> ViolationType.ResourceMismatch
            "UnbufferedIoViolation" -> ViolationType.UnbufferedIo
            "ExplicitGcViolation" -> ViolationType.ExplicitGc

            "LeakedClosableViolation" -> ViolationType.LeakedClosable
            "InstanceCountViolation" -> ViolationType.LeakedActivity
            "SqliteObjectLeakedViolation" -> ViolationType.LeakedSqlObject
            "IntentReceiverLeakedViolation",
            "ServiceConnectionLeakedViolation" -> ViolationType.LeakedRegistration
            "FileUriExposedViolation" -> ViolationType.FileUriExposure
            "CleartextNetworkViolation" -> ViolationType.CleartextNetwork
            "UntaggedSocketViolation" -> ViolationType.UntaggedSocket
            "NonSdkApiUsedViolation" -> ViolationType.NonSdkApi
            "ContentUriWithoutPermissionViolation" -> ViolationType.ContentUriWithoutPermission
            "CredentialProtectedWhileLockedViolation" -> ViolationType.CredentialProtectedWhileLocked
            "ImplicitDirectBootViolation" -> ViolationType.ImplicitDirectBoot
            "IncorrectContextUseViolation" -> ViolationType.IncorrectContextUse
            "UnsafeIntentLaunchViolation" -> ViolationType.UnsafeIntentLaunch

            else -> ViolationType.Unknown
        }
    }

    private fun StrictMode.ThreadPolicy.Builder.applyDetectorsForLegacy(): StrictMode.ThreadPolicy.Builder {
        // detectAll is intentional here — gives broad coverage by default.
        // Consumers can narrow via config.detectedTypes which we honour at
        // violation-time, not at detector-time. (StrictMode doesn't let us
        // toggle individual detectors granularly across API levels.)
        return detectAll()
    }

    private fun StrictMode.VmPolicy.Builder.applyVmDetectorsForLegacy(): StrictMode.VmPolicy.Builder {
        return detectAll()
    }

    private companion object {
        const val TAG = "Strictly"
    }
}
