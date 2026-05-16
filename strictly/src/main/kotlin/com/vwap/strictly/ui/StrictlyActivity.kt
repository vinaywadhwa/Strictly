package com.vwap.strictly.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.vwap.strictly.Strictly
import com.vwap.strictly.core.Session
import com.vwap.strictly.internal.StrictlyRuntime
import com.vwap.strictly.notification.PermissionRequestActivity
import com.vwap.strictly.theme.StrictlyTheme

/**
 * Single entry-point Activity hosting Strictly's 3-screen state machine:
 *
 * 1. [Screen.SessionList]: landing — list of sessions, most recent first
 * 2. [Screen.SessionView]: the violations inside one session
 * 3. [Screen.ViolationDetail]: a single violation's stack trace + metadata
 *
 * Back button always falls one screen back. When launched from the home-screen
 * shortcut, deep-links straight into the live session view so the first-time
 * "open from launcher" feel is "show me what just happened" not "pick from
 * an empty list".
 */
class StrictlyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openedFromShortcut = intent?.getBooleanExtra(EXTRA_FROM_SHORTCUT, false) ?: false

        setContent {
            val themeMode by StrictlyRuntime.requirePrefs().themeMode.collectAsState()
            StrictlyTheme(themeMode = themeMode) {
                StrictlyApp(openedFromShortcut = openedFromShortcut)
            }
        }
    }

    companion object {
        const val EXTRA_FROM_SHORTCUT = "strictly_from_shortcut"
    }
}

/** State machine for which screen is on top. */
private sealed interface Screen {
    object SessionList : Screen
    data class SessionView(val sessionId: String) : Screen
    data class ViolationDetail(val sessionId: String, val fingerprint: String) : Screen
}

@Composable
private fun StrictlyApp(openedFromShortcut: Boolean) {
    val sessions by Strictly.sessions.collectAsState()
    val liveViolations by Strictly.violations.collectAsState()
    val liveSessionId = Strictly.currentSessionId

    // Default landing: live session if it has any violations, otherwise the
    // session list. From the home-screen shortcut, jump straight to the live
    // session even if it's empty so the dev sees "Strictly is listening".
    var screen: Screen by remember(openedFromShortcut, liveSessionId) {
        mutableStateOf<Screen>(
            when {
                openedFromShortcut -> Screen.SessionView(liveSessionId)
                liveViolations.isNotEmpty() -> Screen.SessionView(liveSessionId)
                else -> Screen.SessionList
            },
        )
    }

    var exportingSessionId by remember { mutableStateOf<String?>(null) }
    var settingsVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Shortcut-tap discovery: route through the same persistent shim used by
    // the first-violation auto-ask so we never double-prompt. Respects the
    // [com.vwap.strictly.core.StrictlyConfig.askForNotificationPermission]
    // opt-out flag.
    LaunchedEffect(openedFromShortcut) {
        if (!openedFromShortcut) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return@LaunchedEffect
        val prefs = StrictlyRuntime.requirePrefs()
        if (prefs.notificationPermissionAsked.value) return@LaunchedEffect
        val config = StrictlyRuntime.currentConfig() ?: return@LaunchedEffect
        if (!config.askForNotificationPermission) return@LaunchedEffect
        context.startActivity(PermissionRequestActivity.newIntent(context))
    }

    when (val s = screen) {
        Screen.SessionList -> {
            SessionListScreen(
                sessions = sessions,
                liveSessionId = liveSessionId,
                onSessionClick = { summary -> screen = Screen.SessionView(summary.id) },
                onSettingsClick = { settingsVisible = true },
            )
        }
        is Screen.SessionView -> {
            BackHandler(enabled = true) { screen = Screen.SessionList }
            val session = rememberSessionFor(s.sessionId, liveViolations, liveSessionId)
            if (session == null) {
                // Session vanished (deleted from another surface). Pop back.
                LaunchedEffect(s.sessionId) { screen = Screen.SessionList }
            } else {
                SessionViewScreen(
                    session = session,
                    isLive = s.sessionId == liveSessionId,
                    onBack = { screen = Screen.SessionList },
                    onViolationClick = { v ->
                        screen = Screen.ViolationDetail(s.sessionId, v.fingerprint)
                    },
                    onExport = { exportingSessionId = s.sessionId },
                    onDelete = {
                        if (s.sessionId == liveSessionId) {
                            Strictly.clearCurrent()
                            // Stay on the screen so the empty-state appears.
                        } else {
                            Strictly.deleteSession(s.sessionId)
                            screen = Screen.SessionList
                        }
                    },
                )
            }
        }
        is Screen.ViolationDetail -> {
            BackHandler(enabled = true) { screen = Screen.SessionView(s.sessionId) }
            val session = rememberSessionFor(s.sessionId, liveViolations, liveSessionId)
            val violation = session?.violations?.get(s.fingerprint)
            if (violation == null) {
                LaunchedEffect(s.fingerprint) { screen = Screen.SessionView(s.sessionId) }
            } else {
                ViolationDetailScreen(
                    violation = violation,
                    onBack = { screen = Screen.SessionView(s.sessionId) },
                )
            }
        }
    }

    val sheetSessionId = exportingSessionId
    if (sheetSessionId != null) {
        val sheetSession = rememberSessionFor(sheetSessionId, liveViolations, liveSessionId)
        if (sheetSession != null) {
            ExportSheet(
                session = sheetSession,
                onDismiss = { exportingSessionId = null },
            )
        } else {
            LaunchedEffect(sheetSessionId) { exportingSessionId = null }
        }
    }

    if (settingsVisible) {
        val prefs = StrictlyRuntime.requirePrefs()
        SettingsSheet(
            httpRunning = Strictly.DebugHttp.running,
            httpDesired = Strictly.DebugHttp.desired,
            httpLastError = Strictly.DebugHttp.lastError,
            httpPort = Strictly.DebugHttp.port,
            httpHasSecret = Strictly.DebugHttp.hasSecret,
            onHttpToggle = { Strictly.DebugHttp.setEnabled(it) },
            onHttpRetry = { Strictly.DebugHttp.retry() },
            sessionsState = Strictly.sessions,
            themeMode = prefs.themeMode,
            onThemeChange = { prefs.setThemeMode(it) },
            onWipeAll = {
                Strictly.wipeAllSessions()
                settingsVisible = false
                screen = Screen.SessionList
            },
            onDismiss = { settingsVisible = false },
        )
    }
}

/**
 * Get the [Session] for [sessionId]. For the live session this composes from
 * the in-memory flow so it stays reactive as violations arrive. For archived
 * sessions it reads from disk once and caches in a remember.
 */
@Composable
private fun rememberSessionFor(
    sessionId: String,
    liveViolations: Map<String, com.vwap.strictly.core.Violation>,
    liveSessionId: String,
): Session? {
    return if (sessionId == liveSessionId) {
        // snapshotCurrent is cheap (no IO). Reading liveViolations from the
        // collected state is what re-triggers composition on every violation,
        // so the snapshot stays fresh.
        Strictly.loadSession(sessionId)?.copy(violations = liveViolations)
    } else {
        remember(sessionId) { Strictly.loadSession(sessionId) }
    }
}
