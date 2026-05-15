# Security Policy

## Threat model in one paragraph

Strictly is a **debug-only** Android library. It runs inside an app's debug build. It exposes an **opt-in, loopback-only** HTTP server on `127.0.0.1` so a developer's local machine (via `adb forward`) can read sessions through a companion MCP. The server never binds non-loopback interfaces. The release build wires the no-op artifact, so production users carry zero classes from this library. There is no network egress, no analytics, no third-party telemetry.

## Supported versions

Strictly is in early development. Only the latest `0.x` line receives security fixes.

| Version | Supported |
|---|---|
| 0.1.x | yes |
| < 0.1 | no |

## Reporting a vulnerability

Please do **not** open a public GitHub issue for security-sensitive reports.

Email: **vinay.wadhwa@gmail.com** (subject prefix `[strictly-security]`).

Or use GitHub's private vulnerability reporting: https://github.com/vinaywadhwa/Strictly/security/advisories/new

What to include:
- The version of Strictly affected
- A description of the vulnerability plus its impact
- Steps to reproduce (or a proof-of-concept)
- Suggested fix if you have one

## What to expect after reporting

- Initial acknowledgement within **3 working days**
- A triage plus severity assessment within **7 working days**
- A fix plus coordinated disclosure timeline, typically **within 30 days for critical issues**
- Credit in the release notes (unless you'd rather stay anonymous)

## Hardening guidance for users

A few defaults worth flagging:

- The HTTP debug server is **off by default**. It only starts when the user toggles it in settings or sets `StrictlyConfig.httpDebugAutoStart = true`
- Set `StrictlyConfig.httpDebugSecret` to a random string if you want belt-and-braces auth on top of loopback isolation
- The no-op `strictly-noop` artifact MUST be wired for `releaseImplementation`. Mixing `strictly` into a release build is unsupported. The artifact still won't expose anything off-device, but the size cost is real

## Out of scope

- Bugs that require the developer to deliberately enable the HTTP server, then expose it via reverse-proxy or port-forward to the public internet, are out of scope. Don't do that.
- StrictMode violation false-positives (these are bugs, not security issues)
- The library tripping its own StrictMode policy (self-instrumentation is wrapped in `allowThreadDiskReads`/`allowThreadDiskWrites`, but report a regression as a normal issue)
