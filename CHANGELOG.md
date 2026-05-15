# Changelog

All notable changes to Strictly will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versioning follows [SemVer](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/vinaywadhwa/Strictly/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/vinaywadhwa/Strictly/releases/tag/v0.1.0
