# Strictly: sessions, export, HTTP, MCP (v3)

A proposal doc, not an implementation plan. Captures your brain dump as a concrete architecture so we can react to it before any code changes.

## What's new in v3

1. **Sessions become the core model**. Violations are organised into disk-backed sessions. Strictly opens at the latest session. A back press takes you to a session list. Past sessions are read-only.
2. **Delete scope clarified**. The trash icon clears the *current session's* violations. Past sessions are deleted from the session list (long-press or per-row delete).
3. **Manual copy is per-session**. JSON or Markdown. Works on the live session plus any archived one.
4. **HTTP server is opt-in with a discoverable toggle**. A small settings entry in the session list. Off by default. When on, displays the port plus the `adb forward` command to copy.
5. **MCP is a setup guide, not just a data pipe**. Every error response carries a structured `code`, a `humanMessage`, plus a `nextSteps` array so the calling AI agent can walk the user through fixing it.

## The session model

A session is the bag of violations recorded between one `Strictly.install` and the next. Persisted to disk so it survives process death, rebuilds, reboots.

**Identity**: a `Session` has `id` (UUID), `startedAtMillis`, `lastEventAtMillis`, `appVersionName`, `appVersionCode`, `deviceModel`, `osLevel`, plus the list of `Violation` records.

**Lifecycle**:
- A session is created lazily on the first `ViolationStore.record(...)` call after `Strictly.install`. No violations means no session file is written.
- A session ends implicitly when the next session starts. Same on OS process kill. We never wait for a clean end signal because we won't always get one.
- The current session's JSON file is rewritten on every record, debounced (~500ms) to avoid disk churn during scroll storms.

**Storage layout**:
```
<appPrivateFiles>/strictly/
  sessions.json                 (index: array of session metadata)
  sessions/
    <session-uuid-1>.json
    <session-uuid-2>.json
    ...
```

The index file holds only summary fields (id, startedAt, lastEventAt, uniqueCount, totalEvents, topType). Renders the session list without parsing every full session.

**Retention**: keep last `StrictlyConfig.maxStoredSessions` (default 20). Evict by oldest `lastEventAtMillis`. Both the index entry plus the session file go.

**No PII**. Sessions never persist user-typed content. Stack frames. Types. Counts. The machine metadata listed above. Worth a single line of docs.

## UI flow

Three screens, all hosted by the existing `StrictlyActivity`. No new activities.

1. **Session view** (default landing). Same screen as today. Opens at the *latest* session (live if currently recording, most-recent archived otherwise). Header shows the session start time. Trash icon clears the violations of *this session* only.
2. **Session list** (back from session view). One row per session: relative time ("started 12m ago"), unique count, total events, top type. Tap to open. Long-press to delete. Settings affordance at top right.
3. **Violation detail** (tap a row in session view). Unchanged.

Back stack: `detail` then `session view` then `session list` then exit Strictly. The existing `BackHandler` we already have for `detail` to `session view` just gets one more level.

**Live vs archived**:
- The latest session is live as long as the process is alive. Its `StateFlow` ticks as new violations arrive.
- Archived sessions are read-only. The trash icon is hidden. Replaced by the copy / export affordance. The "back" gesture is the only way to leave them.

**Settings entry**: a small gear icon in the top right of the session list. Opens a sheet with:
- "HTTP debug server" toggle. Off by default. Shows port plus `adb forward tcp:<port> tcp:<port>` command when on.
- "MCP setup" expandable section with copy-pastable claude config snippet (see Tier 3).
- "Storage" row showing `N of 20 sessions stored. <size> on disk.` plus a "Wipe all sessions" destructive action.

## Per-session export (Tier 0)

A "Copy" affordance in the session view header. Tap opens a small bottom sheet with four options:
- Copy as JSON
- Copy as Markdown
- Share as JSON
- Share as Markdown

"Copy" writes to clipboard with a Toast. "Share" fires `ACTION_SEND` so the dev can drop it into Slack, Drive, paste into Claude. Anywhere.

The export is *this session*. No cross-session aggregation in v3.

**JSON shape** (compact, AI-canonical):

```json
{
  "schema": "strictly/session.v1",
  "session": {
    "id": "9c1a...",
    "startedAtMillis": 1747260000000,
    "lastEventAtMillis": 1747261560000,
    "appVersionName": "5.48.0",
    "appVersionCode": 4830,
    "deviceModel": "Pixel 7",
    "osLevel": 36
  },
  "summary": {
    "uniqueCount": 4,
    "totalEvents": 15,
    "byType": { "DiskRead": 3, "UntaggedSocket": 1 },
    "topAppFrameAttributions": [
      { "frame": "com.acme.MainActivity.onCreate:42", "count": 7 }
    ]
  },
  "violations": [
    {
      "fingerprint": "a7c...",
      "type": "DiskRead",
      "occurrenceCount": 7,
      "severity": "medium",
      "firstSeenMillis": 1747260010000,
      "lastSeenMillis": 1747261550000,
      "threadName": "main",
      "message": "",
      "firstAppFrame": null,
      "firstActionableFrame": "com.google.firebase.crashlytics.internal.persistence.FileStore.<init>:81",
      "isThirdPartyOrigin": true,
      "stack": [
        "com.google.firebase.crashlytics.internal.persistence.FileStore.<init>(FileStore.java:81)",
        "..."
      ]
    }
  ]
}
```

**Markdown shape** (dual-purpose, paste-anywhere): a session summary line. A violation table. Per-violation sections with full stack as fenced code blocks. Single-pass readable.

**Effort estimate** (post session-store work): half a day.

## Cross-session diff

Bonus that falls out of sessions for free: pick two sessions in the list, tap "Compare." Renders a diff with three buckets:
- *New* (in the newer session but not the older one, by fingerprint)
- *Resolved* (in the older but not the newer)
- *Unchanged* (in both)

The diff is exportable as its own Markdown / JSON doc. This is what the failed in-memory baseline was trying to be.

**Effort**: 1 day on top of the session store.

## HTTP debug server (Tier 2)

**Opt-in flow**:
1. Off by default. Apps that install Strictly carry zero open-port risk unless the developer actively turns it on.
2. Toggle lives in the settings sheet from the session list (see UI flow above). One tap. The toggle state persists in `SharedPreferences` so the server auto-starts next process boot if previously enabled. Visible state always confirms current status.
3. When on, the settings sheet displays the port (default 8765, configurable via `StrictlyConfig.httpDebugPort`) plus a one-tap "Copy `adb forward` command" affordance.
4. A `StrictlyConfig.httpDebugAutoStart = true` short-circuit for devs who want it always on. Aimed at CI builds.

**Binding**: `127.0.0.1` only. Never `0.0.0.0`. Auth via optional `StrictlyConfig.httpDebugSecret = "..."` checked as a header. Default no secret (loopback-only is the safety boundary).

**Endpoints**:
- `GET /info` returns server version, library version, current session id, session count.
- `GET /sessions` returns the index JSON.
- `GET /sessions/<id>.json` returns the full session.
- `GET /sessions/current.json` returns the live session.
- `GET /sessions/<id>/diff?vs=<otherId>` returns a structured diff.
- `DELETE /sessions/<id>` removes one (current is not deletable via HTTP, only via the UI's trash).
- `POST /sessions/current/clear` clears the live session's violations.

NDJSON variants for the cases where a streaming consumer cares.

**CI use case**: `app/strictly-baseline.json` is checked into the repo. CI runs the app in instrumented mode. Hits `GET /sessions/current/diff?vsFile=app/strictly-baseline.json`. Build fails on non-empty `new` bucket. (Or the agent does the diff locally, in which case CI only needs the live JSON.)

**Effort**: 2 to 3 days.

## MCP companion (Tier 3)

`strictly-mcp` is a separate published artifact. Stdio-based MCP server (Anthropic spec). Talks to Tier 2's HTTP endpoint. Exposes typed tools to Claude Code or any MCP client.

**Tools**:
- `list_sessions()`
- `get_session(id?: string)` (defaults to current)
- `list_violations(sessionId?: string, severity?: "low" | "medium" | "high", originType?: "app" | "thirdParty")`
- `get_violation(fingerprint, sessionId?: string)`
- `diff_sessions(fromId: string, toId: string)`
- `clear_current_session()`
- `suggest_fix_targets(sessionId?: string)`. Server-side ranking that prefers app-frame violations with high counts plus high recency.

**Graceful failure mode**. Every tool response, success or error, follows this envelope:

```json
{
  "ok": false,
  "error": {
    "code": "STRICTLY_HTTP_UNREACHABLE",
    "humanMessage": "Strictly's HTTP debug server is not reachable at http://localhost:8765.",
    "nextSteps": [
      { "kind": "user-action", "text": "Open the Strictly UI in your debug build, tap the gear icon, toggle 'HTTP debug server' on." },
      { "kind": "shell", "text": "adb forward tcp:8765 tcp:8765" },
      { "kind": "config", "text": "If you've changed the port, update strictly-mcp's STRICTLY_HTTP_BASE_URL environment variable." }
    ],
    "diagnostics": {
      "attemptedUrl": "http://localhost:8765/info",
      "underlying": "ECONNREFUSED"
    }
  }
}
```

The codes the AI agent learns to recognise:

- `STRICTLY_HTTP_UNREACHABLE`. Port forward missing or server off.
- `STRICTLY_HTTP_AUTH_REQUIRED`. Secret configured but not provided.
- `STRICTLY_HTTP_AUTH_REJECTED`. Secret mismatch.
- `STRICTLY_SESSION_NOT_FOUND`. Bad id.
- `STRICTLY_NO_SESSIONS`. Store empty. App hasn't recorded anything yet.
- `STRICTLY_LIBRARY_NOT_INSTALLED`. Server replies with `noop` flag (release artifact swapped in).
- `STRICTLY_VERSION_MISMATCH`. MCP version expects a schema the server doesn't speak.

The point: the calling agent reads `nextSteps[]` and can guide the user through *exactly* one of these without needing to know Strictly internals. The MCP is a teacher, not just a wire.

**Setup discoverability**. The session-list settings sheet has an "MCP setup" section that copies a ready-made `claude.json` snippet to clipboard. Plus a tiny "Test MCP connection" button that hits `GET /info` from the device itself and reports back. Lets devs verify the server is reachable from the same loopback the MCP will use.

**Effort**: roughly 1 week. The MCP itself is small. The work is in defining the tool surface plus the error taxonomy plus the doc page that explains it.

## Recommendation

Ship in this order:

1. **Session store + UI flow** (3 days). The session model, the session list screen, the read-only mode for archived sessions, retention, eviction. No new external surface yet.
2. **Per-session export (Tier 0)** (half a day). Copy / share, JSON / Markdown. Trivial on top of the session model.
3. **Cross-session diff** (1 day). Falls out naturally.
4. **HTTP server (Tier 2)** (3 days). Including the settings sheet toggle, the persisted preference, the auto-start config flag.
5. **MCP companion (Tier 3)** (1 week). Error envelope, tool surface, docs, claude.json snippet generator in the settings sheet.

Total: roughly 2.5 to 3 weeks.

## Open questions for me

1. **Session boundary on `Strictly.install`**. Should reinstalling at runtime (config change) start a new session? Or extend the current one? My instinct: start a new one. Keeps the model clean.
2. **Process-name attribution**. Multiprocess apps (eg ones with `:remote` services) record violations from multiple processes. Bucket per-process inside a session? Or keep flat with a `processName` field? I'd say flat for v3, bucket later if needed.
3. **Session list density**. Date headers ("Today", "Yesterday", "May 12") vs flat reverse-chronological? I lean date headers for scannability.
4. **Wipe-all confirmation**. Same Material 3 dialog pattern we use for clear-session, scoped to "wipe all stored sessions" including the current one.
5. **Snapshot vs session terminology**. v2 said "snapshot." v3 calls them "sessions" because they're auto-captured continuously, not on-demand. I think session is the right word. Worth confirming.
6. **MCP transport**. Stdio (standard for Claude Code, simplest) vs HTTP-streamable (lets web-based AIs talk to it). Start stdio. Add HTTP-streamable only if there's pull.
7. **HTTP server library**. `NanoHTTPD` is tiny (~50KB) and Java-friendly. `Ktor` is bigger but already in many apps. I lean NanoHTTPD for minimal footprint on consumers.
