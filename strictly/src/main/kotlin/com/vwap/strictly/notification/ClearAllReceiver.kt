package com.vwap.strictly.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vwap.strictly.Strictly

/**
 * Broadcast target for the "Clear all" notification action. Clears the current
 * session, which causes [LiveNotificationController] to observe an empty map
 * and dismiss the notification. Archived sessions are untouched.
 *
 * Lives outside the manifest registration list because we register it as a
 * package-scoped broadcast (we explicitly set the target package on the Intent
 * in the PendingIntent). Implicit receivers on API 26+ get killed by the system
 * for broadcasts from outside the app — but this is intentionally explicit.
 */
class ClearAllReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Strictly.clearCurrent()
    }
}
