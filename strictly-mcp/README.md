# strictly-mcp

MCP server for [Strictly](https://github.com/vinaywadhwa/Strictly), the debug-only Android StrictMode UI library. Proxies Strictly's on-device HTTP debug server into MCP tools so AI coding agents can read live `StrictMode` violations from a running Android app.

## What it does

Strictly records every `StrictMode` violation your app trips. Violations are deduplicated by stack-fingerprint plus grouped into sessions. The Android library exposes that data over an opt-in loopback HTTP server. This MCP wraps that HTTP server in three tools:

| Tool | Returns |
|---|---|
| `strictly_health` | Library version, current session id, schema id |
| `strictly_list_sessions` | Index of every persisted session, most recent first |
| `strictly_get_session` | Full session JSON (or Markdown) for a given id |

Point your AI agent at it and you can ask things like "what disk reads is my app doing on the main thread right now," "diff the violations between this session and the last one," or "show me the stack for that untagged socket."

## Install

### Claude Code (CLI)

```sh
claude mcp add strictly -e STRICTLY_URL=http://127.0.0.1:8765 -- npx -y strictly-mcp
```

### Any MCP-aware client (Cursor / Continue / others)

Add to your MCP config (typically `~/.mcp.json` or the client's MCP settings):

```json
{
  "mcpServers": {
    "strictly": {
      "command": "npx",
      "args": ["-y", "strictly-mcp"],
      "env": {
        "STRICTLY_URL": "http://127.0.0.1:8765"
      }
    }
  }
}
```

## One-time host setup

The Android library binds `127.0.0.1` on the device, so you need an `adb` bridge from the host to the device. The host port is always `8765` (matches `STRICTLY_URL` above), but the **on-device port differs per app**: Strictly derives it deterministically from `applicationId` so multiple Strictly-enabled apps on the same device never collide.

The simplest path is to copy the exact `adb forward` command from the app itself:

1. Open Strictly in the target app (home-screen shortcut / call `Strictly.openDetailScreen(context)` from a debug menu).
2. Tap the gear icon, expand *Connect to your AI agent*, toggle **Enable**.
3. Tap the copy icon on the `adb forward tcp:8765 tcp:<device-port>` snippet.
4. Paste into your terminal.

Works the same for USB-attached physical devices plus emulators. Re-run once per `adb` daemon session (eg: after a device reboot or `adb kill-server`).

## Configuration

| Env var | Default | Purpose |
|---|---|---|
| `STRICTLY_URL` | `http://127.0.0.1:8765` | Where the MCP looks for the on-device server |
| `STRICTLY_SECRET` | (unset) | Sent as `X-Strictly-Secret` header. Set only if you configured a secret in `StrictlyConfig.httpDebugSecret` |

## Troubleshooting

The MCP returns structured error envelopes with a `code` field, so the agent gets a typed reason instead of a stack trace.

| Code | Means |
|---|---|
| `STRICTLY_HTTP_UNREACHABLE` | The MCP couldn't reach `STRICTLY_URL`. Run `adb forward tcp:8765 tcp:8765`. Check `adb devices`. Confirm the toggle is on in Strictly settings |
| `STRICTLY_HTTP_AUTH_REJECTED` | The HTTP server requires a secret. Set `STRICTLY_SECRET` to the value in your `StrictlyConfig.httpDebugSecret` |
| `STRICTLY_SESSION_NOT_FOUND` | The requested session id doesn't exist on disk. May have been evicted by `maxStoredSessions` |
| `STRICTLY_ROUTE_NOT_FOUND` | Library/MCP version mismatch. Update both ends to the same minor version |

## Compatibility

| MCP version | Strictly schema |
|---|---|
| 0.1.x | `strictly/session.v1` plus `strictly/sessions-index.v1` |

The `/v1/health` endpoint surfaces the live schema. The MCP's `strictly_health` tool returns it directly so the agent can sanity-check before drilling in.

## License

Apache 2.0. See [LICENSE](../LICENSE).
