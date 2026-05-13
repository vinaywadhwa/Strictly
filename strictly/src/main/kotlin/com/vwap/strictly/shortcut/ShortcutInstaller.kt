package com.vwap.strictly.shortcut

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.vwap.strictly.R
import com.vwap.strictly.ui.StrictlyActivity

/**
 * Dynamically registers a long-press-app-icon shortcut for opening Strictly's
 * detail screen.
 *
 * Why dynamic instead of static:
 * - Static shortcuts (xml/shortcuts.xml + <meta-data> in the launcher Activity)
 *   would require the consumer to add a `<meta-data>` entry in *their*
 *   launcher Activity's manifest. That breaks "zero config."
 * - Dynamic shortcuts are pushed by us at runtime via [ShortcutManagerCompat]
 *   and show up on the long-press menu of the consumer's actual launcher
 *   icon automatically. No consumer config needed.
 *
 * Edge cases:
 * - API < 25 has no shortcuts API at all — we no-op.
 * - Already-installed shortcut: pushDynamicShortcut is idempotent, but we
 *   guard with [shortcutAlreadyInstalled] for clarity.
 * - Launcher doesn't support shortcuts (some custom launchers): the call
 *   silently fails, no crash. We don't bother detecting; nothing actionable.
 */
internal class ShortcutInstaller(private val context: Context) {

    fun installIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        if (shortcutAlreadyInstalled()) return

        val openIntent = Intent(context, StrictlyActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(StrictlyActivity.EXTRA_FROM_SHORTCUT, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel(context.getString(R.string.strictly_shortcut_short_label))
            .setLongLabel(context.getString(R.string.strictly_shortcut_long_label))
            .setIcon(IconCompat.createWithResource(context, R.drawable.strictly_ic_shortcut))
            .setIntent(openIntent)
            .build()

        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
        // Intentionally swallow any failure: a debug-only shortcut not appearing
        // is annoying but not actionable, and we don't want a custom launcher's
        // quirk to take down the consumer app at startup.
    }

    private fun shortcutAlreadyInstalled(): Boolean {
        return ShortcutManagerCompat.getDynamicShortcuts(context)
            .any { it.id == SHORTCUT_ID }
    }

    private companion object {
        const val SHORTCUT_ID = "strictly_open_detail"
    }
}
