# DRAFT: Linear story for Branch Android Mobile Platform team

> **Status**: draft for Vinay's review. Not yet posted to Linear.
> **Target team**: Android Mobile Platform (AMP)
> **Suggested priority**: 3 (Medium). System ANR fires but the affected code is third-party (Firebase), so the fix is "init lazily" not "patch a Branch bug".
> **Suggested labels**: none of AMP's existing labels fit cleanly. closest are `Cleanup 2026 Q1` or `Issues Q1 2026`. recommend leaving blank for triage.
> **Suggested project**: leave unset, or fold under `Mobile App Rewrite` if appropriate.
> **Suggested assignee**: unset, team triage.

---

## Proposed title

`Cold-start StrictMode violations triggering ANR. Firebase Remote Config + Perf + Crashlytics blocking the main thread`

---

## Proposed body

## TL;DR

While running [Strictly](https://github.com/vinaywadhwa/Strictly) (a debug-only StrictMode UI I'm building) against Branch's debug build, real main-thread violations surfaced during app cold-start. The cumulative main-thread block is long enough that Android's system ANR watchdog actually fires the "B-Debug isn't responding" dialog. All three high-frequency violation categories trace back to Firebase libraries auto-initing eagerly via their ContentProviders.

| Violation | Root cause | Suggested fix |
|---|---|---|
| ANR during cold start, ~12s main-thread block | Firebase Remote Config + Firebase Perf + Firebase Crashlytics init eagerly from their auto-init ContentProviders. each does SharedPrefs reads (and Crashlytics does untagged socket connects) on the main thread | Init Firebase lazily via Jetpack App Startup with `manualInitialization`. fire from a background coroutine in `BranchApplication.onCreate` |
| `Disk read on main thread` x several | `ConfigSharedPrefsClient.getPersistentFetchTimeoutInSeconds` then `SharedPreferences.awaitLoadedLocked` | Same |
| `Untagged socket` x 11 in first 8s | Firebase Crashlytics `SettingsController$1.lambda$run` opening sockets without `TrafficStats.setThreadStatsTag` | Same. Crashlytics SDK doesn't expose a tag hook. lazy init is the only lever |

## Evidence

### 1. The ANR happens organically during cold start

After `am force-stop` then cold-launch, the main-thread looper was congested enough that a `Handler.postDelayed(4_000L)` callback waited **16 seconds** before firing. Mid-wait, the system ANR watchdog kicked in.

![ANR dialog over Strictly's list](/Users/vinaywadhwa/workspace/Strictly/docs/qa-screenshots/p2_18_list_after_refire.png)

Reproduced 3 times across the overnight session on Pixel 7 emulator (Android 16 / API 36).

### 2. Firebase Remote Config + Firebase Perf doing SharedPrefs.getString on main thread

Full stack from one `Disk read on main thread . unknown origin` violation:

```
android.os.StrictMode$AndroidBlockGuardPolicy.onReadFromDisk          (StrictMode.java:1728)
android.app.SharedPreferencesImpl.awaitLoadedLocked                  (SharedPreferencesImpl.java:283)
android.app.SharedPreferencesImpl$EditorImpl.commitToMemory          (...)
com.google.firebase.remoteconfig.internal.ConfigSharedPrefsClient.getPersistentFetchTimeoutInSeconds (line 110)
com.google.firebase.remoteconfig.internal.RemoteConfigComponent.fetchAndActivate (line 190)
com.google.firebase.perf.config.RemoteConfigManager.getRemoteConfigValue ...
com.google.firebase.perf.config.RemoteConfigManager.getRemoteConfigBooleanResolver ...
```

![Firebase RC + Perf full stack](/Users/vinaywadhwa/workspace/Strictly/docs/qa-screenshots/p2_20_unknown_origin_detail.png)

No `co.branch.*` or `com.branch_international.*` frame anywhere. The entire chain is Android framework then Firebase. Firebase's auto-init ContentProvider runs BEFORE `BranchApplication.onCreate`, so Branch code never gets a chance to dispatch them off the main thread.

### 3. Untagged sockets from Firebase Crashlytics during init

Phase 1 testing on a clean install captured 11 `Untagged socket` VM violations within 8 seconds of cold launch. Stack bottom:

- Firebase Crashlytics `SettingsController$1.lambda$run`
- `java.util.concurrent.{FutureTask, ThreadPoolExecutor}`
- Firebase's `CustomThreadFactory` thread then `Thread.run`

![Crashlytics untagged socket stack](/Users/vinaywadhwa/workspace/Strictly/docs/qa-screenshots/06_violation_detail_untagged_socket.png)
![Stack bottom with Firebase frames](/Users/vinaywadhwa/workspace/Strictly/docs/qa-screenshots/08_violation_detail_stack_bottom.png)

Crashlytics fires these on its own background thread, but the thread isn't tagged with `TrafficStats.setThreadStatsTag(...)`, which is the Vm-policy violation. Not main-thread blocking. it's noise that clutters the signal and could fail strict network-tag enforcement on enterprise builds.

### 4. Full violation distribution after probe-induced refire

For reference: the complete list after a fresh cold-start (12 unique fingerprints, 27 total occurrences in the first few seconds). The bottom row with the orange `x7` chip is a high-frequency unknown-origin Disk read worth drilling into:

![12 unique violations, 27 occurrences](/Users/vinaywadhwa/workspace/Strictly/docs/qa-screenshots/p2_19_list_full.png)

## Suggested fix

### Option A: lazy Firebase init via Jetpack App Startup (recommended)

Add `androidx.startup:startup-runtime` and declare each Firebase component as an `Initializer` with `manualInitialization`. In `BranchApplication.onCreate`:

```kotlin
applicationScope.launch(Dispatchers.IO) {
    AppInitializer.getInstance(applicationContext).apply {
        initializeComponent(FirebaseAppInitializer::class.java)
        initializeComponent(FirebaseCrashlyticsInitializer::class.java)
        initializeComponent(FirebaseRemoteConfigInitializer::class.java)
        initializeComponent(FirebasePerfInitializer::class.java)
    }
}
```

Each Initializer suppresses the corresponding Firebase auto-init ContentProvider via `tools:node="remove"` in the merged manifest. This pushes the SharedPrefs reads and socket opens off the main thread without changing Firebase's own SDK behaviour.

### Option B: triage workaround only

In Strictly's config: `ignoredPackages = listOf("com.google.firebase.")`. Hides the noise from the detection UI but doesn't fix the ANR risk. Use only if Option A is blocked.

## Source data

- Full QA report: `~/workspace/Strictly/docs/strictly_qa_report.md` (514 lines, Phase 1 + Phase 2 findings, screenshots index, recommendations)
- Phase 2 screenshots: `~/workspace/Strictly/docs/qa-screenshots/p2_*.png`
- Probe scaffolding used to surface these (debug-only): `app/src/debug/java/co/branch/app/debug/strictly/StrictlyProbeProvider.kt` in worktree `eager-hofstadter-793551`
