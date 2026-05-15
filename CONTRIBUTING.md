# Contributing to Strictly

Thanks for considering a contribution. This document covers the basics.

## Quick start

```sh
git clone https://github.com/vinaywadhwa/Strictly.git
cd Strictly
./gradlew :strictly:testDebugUnitTest :strictly-noop:testReleaseUnitTest
./gradlew :sample:assembleDebug
```

Open the project in Android Studio. The `sample` app is the live playground.

## Architecture in 30 seconds

| Module | What lives here |
|---|---|
| `strictly` | The real library: StrictMode policy, classifier, fingerprint, session store, HTTP debug server, Compose UI |
| `strictly-noop` | Mirror of the public surface with empty bodies. Wired into release builds for zero-overhead production |
| `strictly-mcp` | Node.js MCP server that proxies the on-device HTTP into Claude Code / Cursor / any MCP-aware client |
| `sample` | A throwaway Android app that consumes Strictly the same way a real consumer would (via Maven Central coords) |

Read flow: `StrictMode penaltyListener` -> classifier -> fingerprint -> `SessionStore` (LRU plus disk) -> two consumers: the `LiveNotificationController` plus the Compose UI (StateFlow).

## What's welcome

- New violation classifiers for `StrictMode` types not yet handled
- New `ignoredPackages` defaults for genuinely-noisy third-party SDKs
- Bug fixes with a regression test
- Doc improvements: typos, clarifications, missing edge cases

## What needs a discussion first

Open an issue before writing code for:

- Changes to the public API surface (`Strictly.kt`, `StrictlyConfig.kt`)
- New persisted JSON fields or schema bumps (touch `SessionJson.SESSION_SCHEMA`)
- New HTTP routes or response shape changes
- Anything that changes `Fingerprint.compute()` semantics (this invalidates everyone's persisted sessions)

## House rules

### Code

- Kotlin 2.0, JDK 17 target
- No XML layouts: Compose only
- New public types must be mirrored in `strictly-noop`. `NoopParityTest` enforces this
- Tests for any new state-machine logic. Tests for any fingerprint behaviour. Tests for any persistence path
- KDoc on every new public type plus method

### Tests

- JVM unit tests via Robolectric live in `strictly/src/test/`. Run with `./gradlew :strictly:testDebugUnitTest`
- New `HttpServerController` or `SessionStore` behaviour needs a test that covers it
- Schema constants (`SESSION_SCHEMA`, `INDEX_SCHEMA`) are pinned by golden tests. Bumping them is a deliberate, versioned act

### Commits + PRs

- Conventional commit prefixes are nice but not required (`feat:`, `fix:`, `docs:`, `chore:`)
- Keep PRs focused on one logical change
- The PR description should explain the *why*, not just the *what*

## Local testing recipes

```sh
# Build + install sample on emulator-5554
./gradlew :sample:installDebug

# Smoke a violation against the real HTTP server
adb forward tcp:8765 tcp:8765
curl -s http://127.0.0.1:8765/v1/health

# Publish library to ~/.m2 for testing in a separate app
./gradlew :strictly:publishToMavenLocal :strictly-noop:publishToMavenLocal -PRELEASE_SIGNING_ENABLED=false
```

In the consuming project's `settings.gradle.kts`, add `mavenLocal()` ahead of `mavenCentral()` to pick up the local snapshot.

## Releasing

Tag-driven via the `release.yml` GitHub Actions workflow. Maintainer steps:

1. Bump `VERSION_NAME` in `gradle.properties` plus `version` in `strictly-mcp/package.json`. They must match
2. Update `CHANGELOG.md` with the new version section
3. Commit: `chore: release v0.X.Y`
4. Tag plus push: `git tag v0.X.Y && git push origin main v0.X.Y`
5. The release workflow runs: gate on tag/version consistency. Run tests. Publish to Maven Central. Publish to npm. Create GitHub Release

If a publish fails mid-flight: fix the issue. Delete the tag (`git tag -d v0.X.Y && git push origin :v0.X.Y`). Re-tag.

## License

By contributing you agree your contribution is licensed under Apache 2.0 (the project's license).
