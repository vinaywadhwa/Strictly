<!-- Thanks for the PR! Fill in the bits below that apply. Strike through what doesn't. -->

## What

A one-liner of what this changes.

## Why

The motivation. What problem does this solve? What use case does it enable?

## How

Pointers to the meaningful files. Tricky parts. Design choices worth flagging.

## Test plan

- [ ] `:strictly:testDebugUnitTest` green locally
- [ ] `:strictly-noop:testReleaseUnitTest` green locally
- [ ] `:sample:assembleDebug` builds
- [ ] Tested on emulator / device (specify which)
- [ ] Screenshot or recording attached (if UI-visible)

## Surface

Tick what this PR touches:

- [ ] Public API (`Strictly.kt`, `StrictlyConfig.kt`)
- [ ] No-op artifact mirror (`strictly-noop/`)
- [ ] Compose UI
- [ ] Notification
- [ ] HTTP debug server
- [ ] MCP server (`strictly-mcp/`)
- [ ] Fingerprint / classifier
- [ ] Persistence / sessions
- [ ] Build / Gradle / CI
- [ ] Documentation only

## Notes for reviewer

Anything you want the reviewer to look at especially carefully.
