package com.vwap.strictly.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.vwap.strictly.internal.StrictlyRuntime

/**
 * Transparent shim that fires the system POST_NOTIFICATIONS permission dialog
 * at the moment Strictly captures its first violation under API 33+ without
 * the permission already granted.
 *
 * This is Strictly's team-discovery moment: one dev adds the gradle dep, every
 * dev on the team sees the prompt the first time the app trips a StrictMode
 * rule on their device. Without this, devs who never explicitly grant
 * POST_NOTIFICATIONS to the host app never see the live notification, and
 * Strictly stays invisible to anyone who didn't add it themselves.
 *
 * Has no UI. The user only sees the system permission dialog. Persists the
 * "asked" flag in onCreate so a process death or user back-out mid-dialog
 * does not cause us to ask again.
 */
internal class PermissionRequestActivity : ComponentActivity() {

    private val launcher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        // Tell the controller to (re)post the notification using the current
        // store state. The triggering violation already fired before this
        // dialog appeared, so without this the user would wait for the next
        // violation to see any notification surface at all.
        StrictlyRuntime.onNotificationPermissionResolved()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Record the ask up front so a back-press or process death mid-dialog
        // does not re-trigger this on the next violation.
        StrictlyRuntime.requirePrefs().setNotificationPermissionAsked(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finish()
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, PermissionRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
