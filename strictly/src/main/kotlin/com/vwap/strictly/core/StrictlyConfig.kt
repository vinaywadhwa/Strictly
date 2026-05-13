package com.vwap.strictly.core

import androidx.annotation.IntRange

/**
 * Runtime configuration for Strictly. Override via [com.vwap.strictly.Strictly.install]
 * before the auto-init provider runs, or via `<meta-data>` tags on the auto-init provider
 * in your debug manifest.
 *
 * All defaults are tuned for "drop this in, see violations immediately, never spam."
 */
data class StrictlyConfig(

    /**
     * Whether Strictly is enabled at all. Default true — but the no-op artifact
     * shipped with `releaseImplementation(strictly-noop)` forces this to false
     * at the artifact level, so production builds carry zero cost.
     */
    val enabled: Boolean = true,

    /**
     * Package prefixes that count as "your code" for fingerprinting and detail-view
     * highlighting. By default we infer this from `Application.packageName`, but
     * you can extend it for multi-module apps (eg `listOf("com.acme.", "com.acme.lending.")`).
     */
    val appPackages: List<String> = emptyList(),

    /**
     * Package prefixes whose violations are silently dropped. Use this for noisy
     * third-party SDKs you can't fix (analytics, payment SDKs, etc).
     *
     * Note: we still drop only if the *originating app frame* is in this list.
     * A violation triggered by an SDK but called from your code is still surfaced.
     */
    val ignoredPackages: List<String> = DEFAULT_IGNORED_PACKAGES,

    /**
     * Which violation types to detect. Default = everything. Trim this if a
     * particular detector is too noisy (eg `ResourceMismatch` for theme libs).
     */
    val detectedTypes: Set<ViolationType> = ViolationType.entries.toSet(),

    /**
     * Maximum number of *unique* violations (by fingerprint) kept in memory.
     * When the store is full, the least-recently-seen entry is evicted.
     * Default 500 is plenty for a single dev session.
     */
    @IntRange(from = 10, to = 10_000)
    val maxStoredViolations: Int = 500,

    /**
     * Whether to update the live notification in real time as new violations
     * arrive. If false, you still get the notification on first occurrence
     * but the count doesn't update — saves battery for very long sessions.
     */
    val liveNotificationUpdates: Boolean = true,

    /**
     * Minimum interval between consecutive notification UI updates, in millis.
     * Acts as a debounce so a scroll storm of identical violations doesn't
     * push 200 notification updates in 100ms.
     */
    @IntRange(from = 0, to = 60_000)
    val notificationUpdateDebounceMillis: Long = 500,

    /**
     * Whether to register the home-screen app shortcut for quick access to
     * the detail screen. Default true. Disable in apps that already have a
     * dense launcher-shortcut list.
     */
    val registerAppShortcut: Boolean = true,

    /**
     * Whether to surface a baseline-style "new violations only" mode in the UI.
     * When enabled, you can mark the current set as "accepted" and the detail
     * screen highlights only violations added after that point.
     */
    val baselineModeEnabled: Boolean = true,

) {
    companion object {
        /**
         * SDKs commonly seen in Android apps that fire StrictMode violations on
         * init or in normal operation, and that the consumer app can't fix.
         * Conservative list — extend if needed, but err on the side of showing.
         */
        val DEFAULT_IGNORED_PACKAGES: List<String> = listOf(
            // Internal Android framework noise
            "com.android.internal.",
            // Google Play Services init reads a few SharedPreferences eagerly.
            "com.google.android.gms.",
        )
    }
}
