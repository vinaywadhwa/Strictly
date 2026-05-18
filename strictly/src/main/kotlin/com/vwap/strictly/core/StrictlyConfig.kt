package com.vwap.strictly.core

import androidx.annotation.IntRange
import com.vwap.strictly.theme.ThemeMode

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
     * Package prefixes treated as "framework noise" when picking a fallback origin
     * frame for display. When a violation's stack has no [appPackages] frame
     * (eg: Firebase auto-init via ContentProvider, before [Application.onCreate]),
     * Strictly surfaces the first frame whose class doesn't start with any of
     * these prefixes. That gives the developer the actually-actionable origin,
     * eg: `FileStore.<init>:81` instead of "unknown origin".
     *
     * Override only if you ship code under one of these prefixes intentionally
     * and want those frames to count as your own.
     */
    val platformPackages: List<String> = DEFAULT_PLATFORM_PACKAGES,

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
     * Maximum number of *archived* sessions kept on disk. The live session is
     * never evicted. Default 20 is plenty for active-development triage.
     */
    @IntRange(from = 1, to = 200)
    val maxStoredSessions: Int = 20,

    /**
     * Device-side port for the debug HTTP server. Null (the default) means
     * "derive deterministically from `Application.packageName`" inside the
     * 8700-8799 band, so:
     * - The same app lands on the same port across reinstalls (your MCP
     *   wiring keeps working without re-registration).
     * - Different Strictly-instrumented apps on one device statistically
     *   land on different ports without collisions.
     *
     * The desktop side of the bridge (the port `STRICTLY_URL` in your MCP
     * config points at) is always [DESKTOP_HOST_PORT] = 8765 regardless. The
     * dev's `adb forward tcp:8765 tcp:<devicePort>` command in the Settings
     * sheet ties the two together.
     *
     * Set an explicit Int (eg 8765) to pin the device port — useful if you're
     * carrying an existing MCP config keyed to a specific port, or you want
     * matching desktop/device numbers for simpler manual `adb forward` typing.
     *
     * Always bound to 127.0.0.1. Server is not started unless the developer
     * explicitly opts in via the in-app settings sheet or via
     * [httpDebugAutoStart].
     */
    @IntRange(from = 1024, to = 65535)
    val httpDebugPort: Int? = null,

    /**
     * If true, start the debug HTTP server automatically on [Strictly.install].
     * False by default. Aimed at CI builds where the developer wants the server
     * up without anyone tapping the toggle.
     */
    val httpDebugAutoStart: Boolean = false,

    /**
     * Optional shared secret required as an `X-Strictly-Secret` header on every
     * HTTP request. Default null (no secret check). The server already binds
     * loopback-only, so a secret is double-protection rather than a hard
     * requirement.
     */
    val httpDebugSecret: String? = null,

    /**
     * Whether Strictly should fire the system POST_NOTIFICATIONS permission
     * dialog the first time a violation is captured on API 33+. Default true,
     * so devs who drop the gradle dep into a team repo get the discovery
     * prompt automatically. Set false if your app handles the prompt elsewhere
     * (eg an onboarding wizard) or to be polite to shared device labs.
     */
    val askForNotificationPermission: Boolean = true,

    /**
     * Default theme for Strictly's own UI when the user has not made an explicit
     * pick from the in-app settings sheet. Defaults to [ThemeMode.System], which
     * mirrors the device's dark/light setting.
     *
     * App data is wiped on uninstall, so a user's runtime pick (stored in
     * SharedPreferences) does not survive reinstalls. Devs who reinstall their
     * builds 100s of times can hard-code their preference here:
     *
     * ```
     * Strictly.install(this, StrictlyConfig(themeMode = ThemeMode.Dark))
     * ```
     *
     * The in-app toggle remains usable as a per-install override of this default.
     */
    val themeMode: ThemeMode = ThemeMode.System,

) {
    companion object {
        /**
         * Desktop-side port the MCP wrapper talks to. Always 8765. Hard-coded
         * on purpose: every `mcp.json` / `claude mcp add` snippet ships with
         * `STRICTLY_URL=http://127.0.0.1:8765`, never changes per-app, so the
         * dev sets MCP config once and it works for every Strictly-enabled
         * app they ever build. `adb forward` is what bridges this constant to
         * whatever device port the library happened to bind.
         */
        const val DESKTOP_HOST_PORT: Int = 8765

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

        /**
         * Default platform-noise prefixes used by [platformPackages]. Covers the
         * Android framework, JDK, Kotlin runtime, and Strictly itself. Keep this
         * tight: the more we exclude here, the more "actionable" the fallback
         * frame is.
         */
        val DEFAULT_PLATFORM_PACKAGES: List<String> = listOf(
            "android.",
            "androidx.",
            "java.",
            "javax.",
            "kotlin.",
            "kotlinx.",
            "libcore.",
            "dalvik.",
            "sun.",
            "com.android.internal.",
            "com.android.server.",
            "com.vwap.strictly.",
        )
    }
}
