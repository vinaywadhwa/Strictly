package com.vwap.strictly.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.vwap.strictly.Strictly
import com.vwap.strictly.theme.StrictlyTheme

/**
 * Single entry-point Activity for Strictly's UI. Hosts the list screen by
 * default; tapping a violation pushes the detail screen.
 *
 * Permission flow:
 * - If launched from the home-screen shortcut (extra [EXTRA_FROM_SHORTCUT] is
 *   true) and we're on API 33+ without POST_NOTIFICATIONS, we request the
 *   permission on first composition. This is the minimum-effort path the dev
 *   needs: long-press app icon -> Strictly -> grant notifications -> done.
 * - Otherwise (eg launched programmatically from a debug menu or from the
 *   notification itself), we don't bother asking — they already have a way in.
 */
class StrictlyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openedFromShortcut = intent?.getBooleanExtra(EXTRA_FROM_SHORTCUT, false) ?: false

        setContent {
            StrictlyTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StrictlyApp(openedFromShortcut = openedFromShortcut)
                }
            }
        }
    }

    companion object {
        const val EXTRA_FROM_SHORTCUT = "strictly_from_shortcut"
    }
}

@androidx.compose.runtime.Composable
private fun StrictlyApp(openedFromShortcut: Boolean) {
    val violations by Strictly.violations.collectAsState()
    var selectedFingerprint by remember { mutableStateOf<String?>(null) }
    var permissionAsked by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Notification permission request, lazily wired only when needed.
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* User's choice is reflected on next violation; nothing to do here. */ }

    LaunchedEffect(openedFromShortcut) {
        if (!openedFromShortcut || permissionAsked) return@LaunchedEffect
        permissionAsked = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val already = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!already) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val selected = selectedFingerprint?.let { violations[it] }
    if (selected != null) {
        ViolationDetailScreen(
            violation = selected,
            onBack = { selectedFingerprint = null },
        )
    } else {
        ViolationListScreen(
            violations = violations.values.toList()
                .sortedByDescending { it.lastOccurrenceAtMillis },
            onViolationClick = { v -> selectedFingerprint = v.fingerprint },
            onClearAll = { Strictly.clear() },
            onMarkBaseline = { Strictly.markBaseline() },
        )
    }
}
