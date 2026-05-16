package com.vwap.strictly.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vwap.strictly.R
import com.vwap.strictly.core.Violation
import com.vwap.strictly.prefs.StrictlyPrefs
import com.vwap.strictly.store.SessionStore
import com.vwap.strictly.ui.StrictlyActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * One persistent, updating notification that mirrors the violation store.
 *
 * Behavior (closely modelled on Chucker's network log notification):
 * - First violation: notification appears, title "Strictly · 1 violation".
 * - Subsequent violations: title count and body text update in place.
 * - Tap notification: opens the detail screen.
 * - Action button: "Clear all" wipes the store, dismisses the notification.
 * - Survives process changes; rebuilt on next violation.
 *
 * Permission handling:
 * - API < 33: no permission required, notify() just works.
 * - API 33+ with POST_NOTIFICATIONS already granted: notify() just works.
 * - API 33+ without POST_NOTIFICATIONS: on the *first* notify attempt we
 *   launch [PermissionRequestActivity] to fire the system dialog. The user
 *   sees the prompt in the context of a real violation (best moment to
 *   ask). Subsequent attempts respect the "asked" flag so we never pester.
 *   If denied, violations still flow into the store and the detail screen
 *   still works via the home-screen shortcut.
 */
@OptIn(FlowPreview::class)
internal class LiveNotificationController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val store: SessionStore,
    private val prefs: StrictlyPrefs,
    private val debounceMillis: Long,
    private val liveUpdates: Boolean,
    private val askForNotificationPermission: Boolean,
) {

    private var collectJob: Job? = null

    fun start() {
        ensureChannel()
        collectJob?.cancel()
        collectJob = scope.launch {
            val flow = store.currentViolations
                .map { it.values.toList().sortedByDescending { v -> v.lastOccurrenceAtMillis } }
                .distinctUntilChanged { old, new -> old.size == new.size && old.firstOrNull()?.id == new.firstOrNull()?.id }

            val finalFlow = if (liveUpdates) flow.debounce(debounceMillis) else flow

            finalFlow.onEach { violations ->
                if (violations.isEmpty()) {
                    safeCancel()
                } else {
                    safeNotify(violations)
                }
            }.collect {}
        }
    }

    fun dismiss() {
        collectJob?.cancel()
        collectJob = null
        safeCancel()
    }

    private fun safeNotify(violations: List<Violation>) {
        if (!canPostNotifications()) {
            maybeRequestPermission()
            return
        }

        val topViolation = violations.first()
        val totalUnique = violations.size
        val totalEvents = violations.sumOf { it.occurrenceCount }

        val openIntent = PendingIntent.getActivity(
            context,
            REQ_OPEN,
            Intent(context, StrictlyActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            pendingIntentFlags(),
        )
        val clearIntent = PendingIntent.getBroadcast(
            context,
            REQ_CLEAR,
            Intent(context, ClearAllReceiver::class.java).apply { setPackage(context.packageName) },
            pendingIntentFlags(),
        )

        val title = context.getString(
            if (totalUnique == 1) R.string.strictly_notification_title_one
            else R.string.strictly_notification_title_many,
            totalUnique,
            totalEvents,
        )

        // Chucker-style: show the top N most recent violations as individual
        // scannable lines via InboxStyle. The dev can triage what's hot right
        // from the shade without opening the detail screen.
        val inbox = NotificationCompat.InboxStyle().setBigContentTitle(title)
        violations.take(MAX_INBOX_LINES).forEach { v ->
            inbox.addLine(formatInboxLine(v))
        }
        if (violations.size > MAX_INBOX_LINES) {
            inbox.setSummaryText(
                context.getString(R.string.strictly_notification_more, violations.size - MAX_INBOX_LINES),
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.strictly_ic_notification)
            .setContentTitle(title)
            .setContentText(formatInboxLine(topViolation))
            .setStyle(inbox)
            .setColor(ContextCompat.getColor(context, R.color.strictly_brand_primary))
            .setColorized(true)
            .setOngoing(true) // sticky like Chucker
            .setOnlyAlertOnce(true) // no sound after the first
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setShowWhen(true)
            .setWhen(topViolation.lastOccurrenceAtMillis)
            .setContentIntent(openIntent)
            .addAction(
                R.drawable.strictly_ic_open,
                context.getString(R.string.strictly_action_open),
                openIntent,
            )
            .addAction(
                R.drawable.strictly_ic_clear,
                context.getString(R.string.strictly_action_clear),
                clearIntent,
            )
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFY_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked at runtime on API 33+; nothing to do but
            // continue accumulating violations in the store. The user can
            // still open the detail screen via the home-screen shortcut.
        }
    }

    private fun safeCancel() {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFY_ID)
        } catch (_: SecurityException) {
            // Same as above.
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.strictly_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.strictly_channel_description)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * One inbox-row format. Designed for scanning in the shade:
     *   ×7  Disk read · MainActivityKt.triggerScrollStorm:131
     *   ×3  Disk write · MainActivityKt.triggerDiskWrite:120
     *   ×1  Network · MainActivityKt.triggerNetwork:126
     *
     * Count first (severity at a glance), then type (what), then location (where).
     * Keep this short — Android truncates inbox lines around 60–80 chars depending
     * on density.
     */
    private fun formatInboxLine(v: Violation): String {
        val count = "×${v.occurrenceCount}"
        val type = shortType(v)
        val where = v.firstActionableFrame?.formatShort() ?: "unknown"
        return "$count  $type · $where"
    }

    /**
     * Trim "on main thread" suffix for the notification because every thread-policy
     * violation is by definition on the main thread, so the suffix wastes space and
     * pushes the location into truncation. VM-level violations keep their full names
     * because they're not redundant ("Untagged socket", "File:// URI exposed", etc).
     */
    private fun shortType(v: Violation): String {
        val full = v.title
        return full.removeSuffix(" on main thread")
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Best-effort launch of the permission shim. Only fires under API 33+ and
     * only once per install: the shim records the "asked" flag in its own
     * onCreate, so we will not re-trigger even on process death mid-dialog.
     *
     * Launching from an [android.app.Application] context on a background thread
     * requires the host app to be in foreground (which it is, by definition,
     * when its main-thread code just tripped StrictMode). If launch is blocked
     * by background-launch policy, the next foreground violation will retry.
     */
    private fun maybeRequestPermission() {
        if (!askForNotificationPermission) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (prefs.notificationPermissionAsked.value) return
        runCatching {
            context.startActivity(PermissionRequestActivity.newIntent(context))
        }
    }

    private fun pendingIntentFlags(): Int {
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
        return PendingIntent.FLAG_UPDATE_CURRENT or mutability
    }

    private companion object {
        const val CHANNEL_ID = "strictly_live_violations"
        const val NOTIFY_ID = 0x57_71_72_70 // 'Wqrp', stable, unlikely to collide
        const val REQ_OPEN = 0xA1A1
        const val REQ_CLEAR = 0xA1A2

        // Android's InboxStyle supports up to ~6 lines visibly before truncation.
        // Five is a comfortable scan target that leaves room for the summary text.
        const val MAX_INBOX_LINES = 5
    }
}
