# Changelog

All notable changes to Strictly will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versioning follows [SemVer](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.1] - 2026-05-18

Polish release. Maven Central: `io.github.vinaywadhwa.strictly:strictly:0.1.1`.

### Added

- `StrictlyConfig.themeMode: ThemeMode` (System / Light / Dark) with a default that survives uninstall via config-side declaration. User overrides persist via `StrictlyPrefs`.
- `StrictlyConfig.askForNotificationPermission` (default `true`). Drives a JIT `POST_NOTIFICATIONS` prompt via a transparent `PermissionRequestActivity` shim on API 33+, so devs discover Strictly on first violation without having to know it exists.
- `Strictly.DebugHttp.port: StateFlow<Int>` exposing the actual bound port. The settings sheet binds its `adb forward` snippet to this flow, so what you copy always matches what is listening.
- `Strictly.DebugHttp.retry()` plus `Strictly.DebugHttp.lastError: StateFlow<String?>` for the in-app retry affordance when a bind fails.

### Changed

- HTTP debug server: host port stays constant at `8765`. On-device port is now derived deterministically from `applicationId` (hash into the 8700 to 8799 band) with a 4-port bind-loop fallback persisted across launches. Single MCP config entry works across every Strictly-enabled app you ever install. `StrictlyConfig.httpDebugPort` is now nullable (`Int? = null`). Pass a value only when you need a known port for scripting.
- Brand: migrated from violet-600 (`#7C3AED`) to neutral graphite (zinc-900 light plus zinc-200 dark) across the Compose UI, the colorized notification, plus the notification small-icon tint. The dev-tool sits next to your IDE. It is not a consumer app fighting for attention.
- Settings sheet: card order changed to Theme then Storage then Connect to your AI agent, so expanding the MCP block keeps the user-facing controls on screen. Footer is a centered linked version label (`Strictly 0.1.1`) instead of a byline.
- README v4: brief-then-detail structure with an `At a glance` summary, neutral-brand screenshots, theming plus deterministic port surfaced in the narrative. Configuration plus programmatic API plus implementation notes collapsed into `<details>` blocks.

### Fixed

- StrictMode self-trip: `StrictlyHttpServer` now wraps both `ServerRunnable` plus the per-request `AsyncRunner` with `TrafficStats.setThreadStatsTag` / `clearThreadStatsTag`. The on-device HTTP server no longer surfaces as an "Untagged socket" violation against the very policy it exists to observe.
- Multi-app disambig dialog: `StrictlyActivity` no longer declares the shared `com.vwap.strictly.action.OPEN` intent-filter. Its `android:taskAffinity` is now empty. A device with two Strictly-enabled apps installed no longer surfaces a chooser when tapping a notification or shortcut. Each app's detail-screen task is isolated.
- Stale persisted port: bind-loop fallback now ignores a persisted `lastBoundPort` that falls outside the current anchor's window. Changing `StrictlyConfig.httpDebugPort` immediately moves the server instead of getting pinned to the previous slot.

### Tests

- `PortDerivationTest`: pins `deriveAnchorPort` band, determinism, plus the `StrictlyPrefs.lastBoundPort` round-trip. The previous release tag shipped a controller-signature change that compiled fine in main but broke the existing controller test on CI. Pure-function coverage tests catch the same class of drift earlier.

## [0.1.0] - 2026-05-15

First public release. Maven Central: `io.github.vinaywadhwa.strictly:strictly:0.1.0`.

### Added

#### Core
- `Strictly` public API with zero-config `ContentProvider` auto-install
- `StrictlyConfig` covering `appPackages`, `ignoredPackages`, `detectedTypes`, `maxStoredSessions`, `notificationUpdateDebounceMillis`, `httpDebugPort`, `httpDebugSecret`, `liveNotificationUpdates`, `baselineModeEnabled`
- `Strictly.violations: StateFlow<Map<fingerprint, Violation>>` for the live session
- `Strictly.sessions: StateFlow<List<SessionSummary>>` across persisted sessions
- `Strictly.currentSessionId`, `loadSession(id)`, `clearCurrent()`, `deleteSession(id)`, `wipeAllSessions()`
- Stable fingerprinting over violation type plus the first 3 app-frames (line numbers excluded so reformats don't invalidate)
- LRU-bounded persistence (`maxStoredSessions`, default 50) with atomic writes plus crash-safe replay

#### UI
- Compose 3-screen state machine: `SessionList` then `SessionView` then `ViolationDetail`
- Live, Chucker-style sticky notification with debounced updates plus de-duplication
- Per-session export sheet for JSON or Markdown
- Settings sheet with: HTTP debug toggle, `adb forward` recipe, MCP install snippets, "wipe all sessions" affordance
- Dynamic home-screen app shortcut (long-press app icon)
- `POST_NOTIFICATIONS` permission flow on API 33+ when launched from shortcut

#### HTTP debug server (opt-in)
- NanoHTTPD-backed loopback server on `127.0.0.1:8765`
- Routes: `GET /v1/health`, `GET /v1/sessions`, `GET /v1/sessions/{id}`, `GET /v1/sessions/{id}.md`
- Optional `X-Strictly-Secret` header auth
- `SO_REUSEADDR` socket option so a fresh process rebinds immediately after APK reinstall
- Structured error envelopes with typed codes for MCP error surfacing

#### Companion MCP server (`strictly-mcp` on npm)
- Three tools: `strictly_health`, `strictly_list_sessions`, `strictly_get_session`
- Structured error envelopes with `nextSteps[]` so the agent can self-teach the user
- Works with Claude Code / Cursor / any MCP-aware client

#### Production safety
- No-op `strictly-noop` artifact mirrors the public surface; zero classes loaded, zero ContentProvider, zero notifications in release builds

#### Tests + CI
- JVM unit tests via Robolectric for fingerprinting, session store, HTTP server controller lifecycle, session schema golden, no-op API parity
- GitHub Actions: PR-time `ci.yml` (compile, test, sample assemble, MCP smoke) plus tag-triggered `release.yml` (Maven Central plus npm publish)

### Requirements

- `minSdk 21` to compile
- API 28+ for the live notification plus detail UI; older APIs fall back to a Logcat-only policy
- API 33+ asks for `POST_NOTIFICATIONS` the first time you open from the shortcut

[Unreleased]: https://github.com/vinaywadhwa/Strictly/compare/v0.1.1...HEAD
[0.1.1]: https://github.com/vinaywadhwa/Strictly/releases/tag/v0.1.1
[0.1.0]: https://github.com/vinaywadhwa/Strictly/releases/tag/v0.1.0
