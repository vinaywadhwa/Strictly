package com.vwap.strictly.internal

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.vwap.strictly.Strictly
import com.vwap.strictly.core.StrictlyConfig

/**
 * Zero-config bootstrap.
 *
 * The Android framework instantiates this ContentProvider during `installContentProviders`,
 * which runs *before* `Application.onCreate`. By using `Application` as the
 * Context here (via `context.applicationContext`) and installing Strictly with
 * default configuration, the consumer app doesn't need to write any code or
 * touch their Application class.
 *
 * Same trick that LeakCanary's auto-init uses, and that Firebase uses via
 * androidx-startup. We use a raw ContentProvider rather than androidx-startup
 * to keep the dependency footprint minimal (no extra library), and because
 * we don't have ordering constraints with other startup initializers.
 *
 * Override the default config: just call `Strictly.install(this, customConfig)`
 * from your `Application.onCreate`. The re-install path tears down the
 * default-bootstrap state cleanly.
 *
 * Skip auto-init entirely: declare a placeholder authority in your debug manifest:
 * ```xml
 * <provider
 *     android:name="com.vwap.strictly.internal.StrictlyAutoInitProvider"
 *     android:authorities="${applicationId}.strictly-auto-init"
 *     tools:node="remove" />
 * ```
 */
internal class StrictlyAutoInitProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val app = context?.applicationContext as? Application ?: return false
        Strictly.install(app, StrictlyConfig())
        return true
    }

    // ---- Unused ContentProvider surface ----
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ) = 0
}
