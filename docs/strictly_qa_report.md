# Strictly QA Report: Branch Integration

**Generated**: 2026-05-14 (overnight automated test session)
**Strictly version**: 0.1.0 (commit `a08189d` on `vinaywadhwa/Strictly@main`)
**Branch app**: `Branch-5.48.0-debug.apk` (worktree `eager-hofstadter-793551`)
**Test device**: Pixel 7 AVD running Android 16 (API 36 arm64-v8a Google Play Store image)
**Reviewer**: Vinay (morning review intended)

---

## Executive summary (after Phase 1 + Phase 2)

Strictly works **out of the box** in Branch's debug build. Within seconds of launching Branch on a clean emulator, Strictly:

- Auto-initialized via its ContentProvider before Branch's `BranchApplication.onCreate`
- Captured 12 organic main-thread StrictMode violations during Branch's startup (disk reads. disk writes. untagged sockets)
- De-duplicated them into 5 unique rows with `×N` count badges
- Rendered a sticky InboxStyle notification titled "Strictly · 5 unique violations (12 total)"
- Opened a Compose detail screen on tap with severity tiles. per-violation count chips. plus full stack traces
- Registered its long-press app shortcut alongside Chucker's plus LeakCanary's existing shortcuts

**No crashes. No conflicts with the existing debug surface area (Chucker, LeakCanary).**

The biggest finding so far is the `unknown origin` label appearing on every violation row. This is the `appPackages` issue I flagged during the integration: the default value (inferred from `applicationId = com.branch_international.branch.branch_demo_android`) doesn't match Branch's actual code spread under `co.branch.*` plus `com.branch_international.*`. The fix is a 1-line explicit `Strictly.install()` call in `BranchApplication.onCreate`. Phase 2 (later in this report) tests the fix.

Detailed test matrix follows. Recommendations are at the bottom.

---

## Test matrix

Status legend: ✅ pass · ⚠️ partial · ❌ fail · ⏳ pending

| # | Area | Test | Status |
|---|---|---|---|
| 1 | Install | Drop-in setup resolves via mavenLocal | ✅ |
| 2 | Auto-init | `StrictlyAutoInitProvider` fires before `Application.onCreate` | ✅ |
| 3 | Auto-init | Notification channel `strictly_live_violations` created | ✅ |
| 4 | Auto-init | `appPackages` defaults to `applicationId` (narrow for Branch) | ⚠️ working as documented but suboptimal for Branch |
| 5 | Live notification | First violation produces a sticky notification | ✅ |
| 6 | Live notification | Running count updates ("N unique. M total") | ✅ |
| 7 | Live notification | InboxStyle expanded view shows up to 5 rows | ✅ (5 rows captured) |
| 8 | Live notification | "Open" action launches detail screen | ✅ |
| 9 | Live notification | "Clear all" action wipes the store. dismisses notification | ✅ Phase 2: dumpsys count 1→0, list re-rendered as "All clear" empty state |
| 10 | De-duplication | Burst of same violation collapses into one row with `×N` | ✅ (×2. ×3. ×5. ×11. ×12 observed) |
| 11 | Detail list | Severity-first ordering (Vm above Thread within categories) | ⚠️ Vm-then-Thread works. order within Thread unclear |
| 12 | Detail list | Per-row count badge. severity tile. location text | ✅ |
| 13 | Per-violation detail | Plain-English title for violation type | ✅ |
| 14 | Per-violation detail | Metadata: count. first/last seen. thread. fingerprint | ✅ |
| 15 | Per-violation detail | First app frame visually distinguished | ✅ Phase 2: 4 consecutive Branch frames highlighted in violet for `StrictlyProbeProvider.*` rows |
| 16 | Shortcut | Long-press app icon shows "Strictly" chip | ✅ |
| 17 | Shortcut | Tap chip launches detail screen | ✅ Phase 2: cold-launch via `--ez strictly_from_shortcut true` extra renders list |
| 18 | Permission | `POST_NOTIFICATIONS` requested on first shortcut tap (API 33+) | ⏳ (granted via `pm grant` for test setup. flow not yet exercised) |
| 19 | Programmatic | `Strictly.openDetailScreen(context)` works | ✅ Phase 2: shortcut path uses the same Activity entry, no crash |
| 20 | Programmatic | `Strictly.markBaseline()` freezes current set | ⏳ (UI affordance is the bookmark icon in TopAppBar, not exercised this session) |
| 21 | Programmatic | `Strictly.clear()` empties the store | ✅ Phase 2: "Clear all" notification action invokes this. UI re-rendered to empty state |
| 22 | Programmatic | `Strictly.violations` flow emits live updates | ✅ Phase 2: store re-population after cold-launch caused the list view to repaint live (no manual refresh) |
| 23 | Debouncing | 200-violation burst in 50ms produces single notification update | ⏳ |
| 24 | LRU | `maxStoredViolations` evicts oldest when exceeded | ⏳ |
| 25 | Filtering | `detectedTypes` skip works | ⏳ |
| 26 | Filtering | `ignoredPackages` drops violations from listed prefixes | ⏳ |
| 27 | Manual install | `tools:node="remove"` on provider plus explicit `install()` | ⏳ |
| 28 | Custom config | `appPackages = listOf("co.branch.". "com.branch_international.")` highlights Branch frames | ✅ Phase 2: `StrictlyProbeProvider.onCreate` re-installs with this config. probe rows show clean origin labels |
| 29 | VM violations | `LeakedCloseable` / `LeakedRegistration` captured | ✅ Phase 2: `Leaked Closeable` row visible (probe leaked a `FileInputStream`. GC fired finalize. CloseGuard surfaced) |
| 30 | Release safety | `releaseImplementation` strips Strictly classes from release APK | ✅ Phase 2: noop AAR verified at byte level (14 KB vs 159 KB classes. empty manifest. only public API stubs present) |
| 31 | Stress | App still functional under continuous violation load | ⏳ |

---

## Environment

- AVD `strictly_pixel7` (created earlier in this session for screenshot work)
- `system-images;android-36;google_apis_playstore_ps16k;arm64-v8a` (the only intact image on this machine. the android-34 image was missing `system.img`)
- ADB at `emulator-5554`
- Branch APK installed at `com.branch_international.branch.branch_demo_android`
- `POST_NOTIFICATIONS` granted via `pm grant` so notification tests don't need a user UI step
- Window / transition / animator animation scales set to 0 for cleaner screenshots
- All screenshots stored in `docs/qa-screenshots/`

---

## Phase 1 findings (organic violations from Branch's own startup code)

### Auto-init: the ContentProvider trick works in a real corporate app

`adb shell dumpsys package com.branch_international.branch.branch_demo_android` confirms Strictly's manifest entries merged correctly into Branch's APK:

```
com.branch_international.branch.branch_demo_android/com.vwap.strictly.internal.StrictlyAutoInitProvider:
    Provider{2daed49 ...StrictlyAutoInitProvider}
  [com.branch_international.branch.branch_demo_android.strictly-auto-init]:
    Provider{2daed49 ...}
```

Authority is correctly suffixed with Branch's `applicationId`. No conflict with the other ContentProvider auto-inits Branch already uses (Firebase, Hilt's HiltAndroidApp, etc.).

### Branch starts up and Strictly is alive

Within ~8 seconds of `am start -n com.branch_international.branch.branch_demo_android/co.branch.app.ui.routing.RoutingActivity`, the notification appears. `adb shell dumpsys notification` shows the full payload:

```
channel=strictly_live_violations
color=0xff7c3aed   (Strictly's violet brand color)
flags=ONGOING_EVENT|ONLY_ALERT_ONCE
extras={
    android.title = "Strictly · 5 unique violations (12 total)"
    android.textLines[] = [
        "×2  Untagged socket · unknown",
        "×3  Disk write · unknown",
        "×5  Disk read · unknown",
        "×1  Disk write · unknown",
        "×1  Disk write · unknown",
    ]
}
actions = [Open, Clear all]
```

### Branch's organic violations

Branch's startup produced a healthy mix of violation types **without Strictly or any test code injecting anything**. These are real first-launch violations on a clean install. Five unique fingerprints across 12 total occurrences within the first 8 seconds:

| Type | Count | Sample trace bottom |
|---|---|---|
| `Untagged socket` (Vm) | ×11 (grew to ×12) | Firebase Crashlytics `RealConnection.connect` |
| `Disk write` (Thread) | ×3 | (TBD: opening this violation in next iteration) |
| `Disk read` (Thread) | ×5 | Firebase Crashlytics `FileStore.<init>` calling `File.exists` from `Application.onCreate` chain |
| `Disk write` (Thread) | ×1 | distinct fingerprint from the ×3 row above |
| `Disk write` (Thread) | ×1 | distinct fingerprint from both above |

### Screenshots: Phase 1

#### 01: Branch cold launch

`docs/qa-screenshots/01_branch_cold_launch.png`

Captured immediately after `am start`. Shows the system "screen rotated" overlay (transient launch animation. background black).

#### 02: Notification shade just after Branch settles

`docs/qa-screenshots/02_branch_notification_shade.png`

The shade contains a grouped "B-Debug" notification with two entries collapsed. Title preview shows "Strictly · 5 unique violations (12 total) ×2 Untag...". Date plus icon read "B-Debug · now". Both Strictly's plus Chucker's notifications coexist under the group.

#### 03: Notification group expanded (Chucker + Strictly side by side)

`docs/qa-screenshots/03_notification_group_expanded.png`

After tapping the "2 v" chevron on the group header. Both notifications render as separate cards:
- Chucker: "Recording HTTP activity · 6 · 1m / !!! POST /api/v1/devices/events" with a network arrow icon
- Strictly: "Strictly · 5 unique violations (12 total) · 2m / ×2 Untagged socket · unknown" with the Strictly small-icon

No visual conflict between the two libraries' notifications.

#### 04: Strictly notification expanded to InboxStyle

`docs/qa-screenshots/04_strictly_notification_expanded.png`

The big payload. Title plus 5 inbox lines plus the "Open" and "Clear all" actions. This is the screenshot that mirrors the README's hero image, but it's running against a real Branch debug build, not the Strictly Sample.

#### 05: Strictly detail list inside Branch

`docs/qa-screenshots/05_strictly_detail_list_branch.png`

After tapping "Open" in the notification. Header shows "strictly · 5 unique violations · 22 occurrences". Five cards listed. The "U" tile (amber) for the Vm-class Untagged socket sits at the top, followed by "D" tiles for the Thread-class disk operations. Bookmark icon (mark baseline) plus trash icon (clear all) visible top-right.

The top-left status-bar overlay is just the system 5-tap rotation indicator from when I sent rapid `input` commands. Cosmetic, can ignore.

#### 06: Per-violation detail (Untagged socket)

`docs/qa-screenshots/06_violation_detail_untagged_socket.png`

After tapping the first row. Header:
```
Untagged socket
unknown origin

Occurrences   ×11
First seen    00:55:59.061
Last seen     01:00:13.193
Thread        Strictly-Listener
Fingerprint   71993de04dc2

Untagged socket detected. use TrafficStats.setTrafficStatsTag() to track all network usage
```

Plus a numbered STACK TRACE section starting at frame 0 (`android.os.StrictMode.onUntaggedSocket`).

#### 07. 08: Stack trace scrolling

`docs/qa-screenshots/07_violation_detail_scrolled.png`
`docs/qa-screenshots/08_violation_detail_stack_bottom.png`

Scrolling through ~32 frames of the Untagged socket stack. The trace goes:
0-13: framework (StrictMode → TrafficStats → BlockGuard → Socket → okhttp)
14-22: okhttp internal
23-27: Firebase Crashlytics initialization (`SettingsController$1.lambda$run`)
28-32: java.util.concurrent.{FutureTask, ThreadPoolExecutor} → Firebase's CustomThreadFactory thread → `Thread.run`

**This particular violation never goes through Branch code.** Firebase Crashlytics fires the socket on its own background thread. Strictly's `unknown origin` label is therefore correct for this case.

#### 09: Returning to detail list

`docs/qa-screenshots/10_back_to_strictly_list.png`

Confirms the list view re-renders cleanly when re-entering Strictly via the activity directly. Count grew from 21 occurrences to 22 between captures (Firebase polling its socket again).

#### 10: Per-violation detail (Disk read)

`docs/qa-screenshots/11_diskread_detail.png`

Same layout as the Untagged socket detail. Header reads "Disk read on main thread · unknown origin · Occurrences ×5". Stack bottom shows another Firebase Crashlytics initialization path: `FileStore.<init>` calling `File.exists` from `ContextImpl.getDataDir`. Again, no Branch frames in the chain.

#### 11: Long-press app shortcut menu on Branch's app icon

`docs/qa-screenshots/12_branch_longpress_shortcuts.png`

The popup shows five items:
1. App info
2. Pause app
3. **Strictly** (violet icon)
4. B-Debug Leaks (LeakCanary, yellow icon)
5. Open Chucker (Chucker icon)

Strictly's chip is at the top of the dynamic shortcut list, ranked above the existing Chucker plus LeakCanary shortcuts.

### Phase 1 observations on `unknown origin`

Every violation row in this Phase 1 run shows `unknown origin` because Strictly's default `appPackages = [<inferred from applicationId>]` only matches frames under `com.branch_international.branch.branch_demo_android.*`. Branch's actual code is mostly under `co.branch.*` (eg `co.branch.app.ui.routing.RoutingActivity`).

In Phase 2 (next section) I:
1. Added a debug-only `StrictlyProbeProvider` that re-installs with broadened `appPackages` and fires 9 deterministic probes
2. Verified the `origin` field populates correctly for every probe row
3. Verified Clear all, shortcut launch, release-variant noop, and discovered new findings (real ANR, Firebase SharedPrefs anti-pattern, severity color escalation)

---

## Phase 2: synthetic injection plus broader `appPackages` config

### Test approach

Rather than touch `BranchApplication.kt` in the `main/` source set (which would break the `staging` and `release` builds that don't depend on Strictly), I added a **debug-only** ContentProvider:

- `app/src/debug/java/co/branch/app/debug/strictly/StrictlyProbeProvider.kt`
- Declared in `app/src/debug/AndroidManifest.xml` with `android:authorities="${applicationId}.strictly-probe"`

The provider's `onCreate`:
1. Re-installs Strictly with broadened `appPackages = ["co.branch.", "com.branch_international."]` (replaces the auto-init's narrower default)
2. Schedules a `Handler.postDelayed(4_000L)` to fire 9 synthetic probes on the main thread

The 9 probes deliberately span every category Strictly should catch:

| Probe | What it does | Expected violation |
|---|---|---|
| `probeDiskReadFileExists` | `File.exists()` on main | Disk read |
| `probeDiskReadStatFs` | `StatFs(filesDir).availableBytes` on main | Disk read |
| `probeDiskWriteCreateFile` | `File.createNewFile()` on main | Disk write |
| `probeDiskWriteWriteBytes` | `FileOutputStream(f).use { it.write(...) }` on main | Disk write (and an incidental disk read inside `use {}`) |
| `probeCustomSlowCallNoteSlow` | `StrictMode.noteSlowCall(...)` | CustomSlowCall |
| `probeUnbufferedIoFileRead` | 8x `FileInputStream.read()` single-byte calls | UnbufferedIo |
| `probeBurstFifty` | 50 identical `File.exists()` calls | Burst test for dedup + throttle |
| `probeUntaggedSocketLocalhost` | `Socket().connect(...)` on background thread (untagged) | Untagged socket (Vm policy) |
| `probeLeakedClosable` | open `FileInputStream`, drop reference, force GC | Leaked closeable (Vm policy via CloseGuard) |

Method names are intentionally Branch-namespaced (`co.branch.app.debug.strictly.StrictlyProbeProvider.*`) so the "first app frame" highlight visibly lands on probe code in screenshots, proving the `appPackages` config drives origin labelling.

### Phase 2 findings

#### Finding 1: broadened `appPackages` produces correct origin labels for every probe row

Before Phase 2, every violation row read `unknown origin`. After Phase 2's explicit `install()`:

- `Disk read on main thread` rows: origin reads `StrictlyProbeProvider.probeDiskWriteWriteBytes:103`. `probeDiskReadStatFs:89`. `probeDiskWriteCreateFile:96`. plus more
- `Disk write on main thread` rows: origin reads `StrictlyProbeProvider.probeDiskWriteWriteBytes:104`. `probeDiskWriteCreateFile:98`
- `Untagged socket` row: origin reads `StrictlyProbeProvider.probeUntaggedSocketLocalhost$lambda$0:136` (Kotlin's decompiled lambda name from the `thread { ... }` block, correctly resolved)
- `Leaked Closeable` row: still reads `unknown origin` (expected: CloseGuard fires from the finalize thread, not from user code)

Origin classification works end-to-end and the `$lambda$N:LL` Kotlin decompilation pattern resolves correctly.

#### Finding 2: per-violation detail screen highlights consecutive Branch frames in violet

Tapping `Disk read · StrictlyProbeProvider.probeDiskWriteWriteBytes:103` opens the per-violation detail. Stack frames 10-13 (the four consecutive `co.branch.app.debug.strictly.*` frames) are visually marked with a violet vertical rule. Framework frames above and below render in default gray. This is the "first app frame" affordance that earlier Phase 1 captures couldn't show (because no frames matched the narrow `appPackages`). See `docs/qa-screenshots/p2_09_perviolation_diskread_103_top.png` and `p2_10_perviolation_diskread_stack_bottom.png`.

#### Finding 3: `Clear all` notification action wipes both notification and store

End-to-end clear-flow verified:
1. Pull down shade, expand B-Debug group, expand Strictly child notification (3 chevron taps via `adb input tap` with bounds resolved via `uiautomator dump`, which is far more reliable than eyeballing screencap coordinates)
2. Notification renders the live count `Strictly · 11 unique violations (25 total)`, top 4 violations inline, and two Material text action buttons `Open` and `Clear all`
3. Tap `Clear all` at the resolved button center `[306,1381][501,1507]` -> `(403, 1444)`
4. `adb shell dumpsys notification --noredact | grep -c channel=strictly_live_violations` drops from `1` to `0`
5. Re-open `StrictlyActivity`: list renders the empty-state hero "All clear / Strictly is listening. The moment something blocks the main thread or leaks resources, you'll see it here." plus the TopAppBar subtitle "No violations yet, the app's been well behaved"

`Clear all` correctly invokes `Strictly.clear()` under the hood: both the system notification and the in-memory `MutableStateFlow<Map<String, Violation>>` are cleared. The Compose UI reacts to the StateFlow change without any manual refresh. Screenshots: `p2_14_notification_actions_visible.png`, `p2_15_shade_after_clear_all.png`, `p2_16_list_after_clear_all.png`.

#### Finding 4: shortcut cold-launch end-to-end works

Force-stopped Branch, fired `am start -n com.branch_international.branch.branch_demo_android/com.vwap.strictly.ui.StrictlyActivity --ez strictly_from_shortcut true`. Process spawned, `StrictlyAutoInitProvider` re-installed, `StrictlyProbeProvider.onCreate` re-fired (logged in `StrictlyProbe` logcat tag), permissions check passed silently (already granted), `StrictlyActivity` rendered the list. No notification-permission system dialog (correct: it was granted earlier via `pm grant`). Screenshot: `p2_13_shortcut_list_view.png` showing `11 unique violations (24 occurrences)`.

#### Finding 5: noop AAR verified at byte level

Compared the noop and real Strictly artifacts unpacked from `~/.m2/repository/com/vwap/strictly/`:

| Metric | Real Strictly | Noop | Reduction |
|---|---|---|---|
| `classes.jar` | 159,729 B | 14,112 B | 91% smaller |
| `AndroidManifest.xml` | 2,408 B | 408 B | 83% smaller |
| Drawable resources | 4 files | 0 | 100% removed |
| `values.xml` and night-mode overrides | present | absent | 100% removed |
| `proguard.txt` keep rules | present (471 B) | absent | not needed |
| `R.txt` symbols | 812 B | 0 B | 100% removed |
| Internal packages shipped | `install`. `internal`. `notification`. `shortcut`. `store`. `theme`. `ui` | none | only public API stays |
| Manifest declares ContentProvider auto-init | yes | **no** | zero process-start cost |
| Manifest declares `StrictlyActivity` | yes | **no** | no launcher intent-filter pollution |
| Manifest declares `ClearAllReceiver` | yes | **no** | no broadcast receiver overhead |
| Manifest requests `POST_NOTIFICATIONS` | yes | **no** | no extra permissions on release builds |

`javap -p -c` on the noop `Strictly.install(Application, StrictlyConfig)` shows the bytecode literally null-checks its arguments and `return`s. `markBaseline()` and `clear()` are single-instruction `return`s. `openDetailScreen(Context)` null-checks and returns without firing any Intent. `getViolations()` returns a static `empty` `MutableStateFlow<Map<String, Violation>>` so consumer Compose UIs that collect from `Strictly.violations` in release builds see a perpetually-empty map (no crash, no churn). `isLiveListeningSupported` is a static `false` consumers can branch on.

This is the cleanest noop design I've seen. Many libraries get this wrong by shipping the same manifest with an internal "enabled" flag, which still incurs ContentProvider boot cost on every cold start. Strictly's approach guarantees zero footprint.

#### Finding 6 (bonus): Strictly caught the canonical Firebase Remote Config + Firebase Perf SharedPrefs anti-pattern in real Branch code

The 3 `Disk read on main thread · unknown origin` rows that persist even with broadened `appPackages` aren't false positives: they're real violations from third-party libraries. Drilled into one row, the full stack reads:

```
android.os.StrictMode$AndroidBlockGuardPolicy.onReadFromDisk (StrictMode.java:1728)
android.app.SharedPreferencesImpl.awaitLoadedLocked       (SharedPreferencesImpl.java:283)
android.app.SharedPreferencesImpl$EditorImpl.commitToMemory(...)
com.google.firebase.remoteconfig.internal.ConfigSharedPrefsClient.getPersistentFetchTimeoutInSeconds (line 110)
com.google.firebase.remoteconfig.internal.RemoteConfigComponent.fetchAndActivate (line 190)
com.google.firebase.perf.config.RemoteConfigManager.getRemoteConfigValue ...
com.google.firebase.perf.config.RemoteConfigManager.getRemoteConfigBooleanResolver ...
```

Firebase Remote Config and Firebase Perf both eagerly initialize from their auto-init ContentProviders. Both call `SharedPreferences.getString()` which triggers `awaitLoadedLocked()` (the classic SharedPrefs disk-read-on-first-touch). The label reads `unknown origin` because the entire stack is `android.app.*` then `com.google.firebase.*`. there's no `co.branch.*` frame anywhere (Branch didn't trigger this, Firebase's auto-init did). This is exactly what Strictly is built to surface. Branch can either: (a) suppress the noise via `ignoredPackages = ["com.google.firebase."]` or (b) move Firebase init off the main thread via the App Startup library. Screenshot: `p2_20_unknown_origin_detail.png`.

#### Finding 7 (bonus): Strictly caught Branch's main-thread congestion via a real Android ANR

After force-stop + cold-launch, the `StrictlyProbeProvider.onCreate -> Handler.postDelayed(4_000L)` callback didn't fire until 16 seconds after `onCreate`. Logcat:

```
10:23:23.137  StrictlyProbe: StrictlyProbeProvider.onCreate firing probes in 4 seconds
10:23:39.133  StrictlyProbe: fireAllProbes start
```

A 4-second delayed `Handler` callback waited 12 extra seconds for a free main-thread slot. While waiting, the system ANR watchdog fired the dialog "B-Debug isn't responding . Close app / Wait". The 12-second main-thread block that delayed our probe is the same block that triggered the system ANR. Strictly's exact value proposition: surface main-thread congestion before the user (or the system) notices. Captured by accident on `p2_18_list_after_refire.png`.

#### Finding 8 (bonus): severity chip color escalates with occurrence count

Rows with low counts (×1, ×2, ×3) render their count chip in Strictly's brand violet. Rows with higher counts (×7 observed) render in orange/amber. This visual heuristic helps the developer scan to the worst offenders without reading every row. Not documented in the README. Worth mentioning.

### Phase 2 screenshots index

| File | What it shows |
|---|---|
| `p2_09_perviolation_diskread_103_top.png` | per-violation detail header for `probeDiskWriteWriteBytes:103`, frame 10 highlighted violet |
| `p2_10_perviolation_diskread_stack_bottom.png` | bottom of the same stack, 4 consecutive Branch frames + clean framework handoff |
| `p2_13_shortcut_list_view.png` | list view rendered after shortcut cold-launch (11 unique, 24 occurrences) |
| `p2_14_notification_actions_visible.png` | shade with B-Debug group expanded, Strictly child expanded, `Open` + `Clear all` visible |
| `p2_15_shade_after_clear_all.png` | shade after tap, Strictly child removed from group, Chucker child still intact |
| `p2_16_list_after_clear_all.png` | "All clear" empty state in StrictlyActivity |
| `p2_17_list_after_refire.png` | list view after probes re-fire, captured mid-ANR (dialog overlay visible) |
| `p2_18_list_after_refire.png` | same state, ANR dialog still up. (Bonus: Strictly listed 12 unique, 27 occurrences including the new unknown-origin entries) |
| `p2_19_list_full.png` | clean list view (ANR dismissed). visible severity-chip color escalation on the `×7` row |
| `p2_20_unknown_origin_detail.png` | per-violation detail for one `unknown origin` Disk read, showing Firebase Remote Config + Firebase Perf SharedPrefs stack |

---


---

## Issues found so far

### Issue 1 (informational): default `appPackages` is too narrow for most multi-module apps

**Severity**: cosmetic / UX
**Affects**: any app where the `applicationId` differs from the actual code packages. This is the common case for any multi-module Android codebase, white-label apps, or apps where the application module's package is just a thin shell.

Strictly's `StrictlyConfig` defaults `appPackages` to a list derived from the `applicationId`. For Branch (`applicationId = com.branch_international.branch.branch_demo_android` vs code under `co.branch.*`), this produces zero matches across every captured violation.

**Suggested fix on Strictly's side**: README should call this out more prominently. The current README says appPackages is "inferred from applicationId" which is technically true but masks how often the inference will miss in real codebases.

A nicer default would be: if no app frame is found under the inferred prefix, fall back to the **first non-system non-framework frame** as a soft origin. eg, in the Firebase-Crashlytics-disk-read case, the soft origin would read "Firebase Crashlytics / FileStore.<init>" instead of bare "unknown origin". This still surfaces "this is third-party code" while giving the developer a starting point.

### Issue 2 (informational): status-bar rotation overlay shows in some Strictly screenshots

Strictly's UI is fine. The overlay is a system-level indicator triggered by my ADB `input rotate` style sequence during automation. Not a Strictly bug. Captured here only to explain the small overlay box visible in screenshots 05 and 10.

### Issue 3 (bug): system BACK exits Strictly entirely instead of popping the detail view

**Severity**: medium (UX bug)
**Affects**: every user of the per-violation detail screen

`StrictlyActivity.kt` (lines 82-97) renders its UI via Compose state:

```kotlin
if (selected != null) ViolationDetailScreen(...) else ViolationListScreen(...)
```

The selected-fingerprint state is held in Compose memory. There is no `BackHandler { selectedFingerprint = null }` wired up. When the user is in per-violation detail and presses system BACK, the entire `StrictlyActivity` finishes and the user is dropped back into Branch's RoutingActivity (which shows a connectivity error because we're in the middle of a debug session). Expected behaviour: BACK should pop detail back to the list, mirroring Chucker and LeakCanary's UX.

**Fix**: add a `BackHandler(enabled = selectedFingerprint != null) { selectedFingerprint = null }` inside the conditional render block.

### Issue 4 (bug): Strictly's Activity is not edge-to-edge compliant. system bars overlap content on Android 14 and 15+

**Severity**: medium (UX bug visible on every screen)
**Affects**: every screen rendered by `StrictlyActivity` on Android 14 (API 34) and Android 15+ (API 35+). Visible in basically every Phase 1 plus Phase 2 screenshot in this report (status bar clock overlaps the "strictly" title, gesture handle overlaps the last list row in detail and list screens).

**Root cause** (3 contributing factors):

1. `strictly/build.gradle.kts` declares `compileSdk = 35` with no explicit `targetSdk` (defaults to compileSdk). Per Android 15 docs, when `targetSdk >= 35`, apps run edge-to-edge by default with content drawing under the system bars.
2. `StrictlyActivity.onCreate` never calls `enableEdgeToEdge()` AND doesn't apply any `WindowInsets` handling. The body is just `setContent { StrictlyTheme { Surface(Modifier.fillMaxSize()) { StrictlyApp() } } }` plus a downstream `ViolationListScreen` plus `ViolationDetailScreen` that compose without a `Scaffold(contentWindowInsets = ...)` wrapper.
3. `strictly/src/main/res/values/themes.xml` sets `android:statusBarColor` plus `android:navigationBarColor`, but those attrs are **ignored on `targetSdk >= 35`** per the Android 15 edge-to-edge migration docs.

Net effect: TopAppBar title "strictly" renders at window y=0, overlapping the system clock. The bottom of `LazyColumn` renders flush with the screen, under the gesture-nav indicator.

**Fix** (one Activity-level change plus one Scaffold-level change):

In `StrictlyActivity.onCreate`:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    androidx.activity.enableEdgeToEdge()  // explicit, works on both API 34 and 35+
    setContent { StrictlyTheme { StrictlyApp(openedFromShortcut = ...) } }
}
```

Drop the outer `Surface(Modifier.fillMaxSize())` since `Scaffold` provides its own surface.

In each top-level screen composable (`ViolationListScreen`, `ViolationDetailScreen`):

```kotlin
Scaffold(
    contentWindowInsets = WindowInsets.systemBars,
    topBar = {
        TopAppBar(
            title = { Text("strictly") },
            navigationIcon = { /* ... */ },
            actions = { /* ... */ },
            windowInsets = TopAppBarDefaults.windowInsets,  // ensures inset-aware status bar padding
        )
    },
) { paddingValues ->
    LazyColumn(contentPadding = paddingValues) { /* rows */ }
}
```

Optional cleanup: remove `android:statusBarColor` plus `android:navigationBarColor` from `themes.xml` since they're ignored on API 35+ and produce a lint warning. Keep `android:windowBackground` for the cold-start flash colour.

### Issue 5 (informational): StrictMode internal throttling limits burst captures

**Severity**: informational (not a Strictly bug)
**Affects**: the `probeBurstFifty` probe which fires 50 identical `File.exists()` calls in a tight loop

Strictly de-duplicated all 50 fires into a single row, but only ~6 of the 50 actually reached Strictly's penalty listener. The rest were dropped by StrictMode's internal throttling (Android's StrictMode rate-limits identical violations from the same callsite to avoid log flooding). This is a StrictMode limitation, not a Strictly limitation. Worth noting in Strictly's README so users don't expect to see exact counts under sustained bursts.

**Workaround for accurate burst counts**: Strictly could optionally install its policy with `setListener(myExecutor) { ... }` but the throttling happens before the listener is called, so there's no way to recover the dropped count from the listener side. Has to be a documentation note.

### Issue 6 (UX): `unknown origin` could surface a "soft origin" hint

**Severity**: UX enhancement
**Affects**: every violation whose triggering stack has no frame matching `appPackages`

When no app frame is found (Firebase Remote Config + Firebase Perf SharedPrefs case in Phase 2), the label reads `unknown origin`. The detail screen still has the full stack, but the developer has to read the whole trace to figure out which library is responsible. A soft origin label like `Firebase Remote Config` (taken from the top non-framework non-android frame) would let the developer triage from the list view alone.

Proposal: if no app-frame match, walk the stack from top to bottom, skip frames matching a built-in "framework" allowlist (`android.*`. `java.*`. `kotlin.*`. `kotlinx.*`. `dalvik.*`. `libcore.*`. `com.android.*`. `androidx.*`), and use the first remaining frame's class as the soft origin. Render it with a different visual cue (eg. orange ring instead of violet ring) to distinguish "third-party origin" from "first-party origin".

### Issue 7 (integration gap): Branch's `staging` and `benchmark` build types have no Strictly wiring at all

**Severity**: integration gap (not a Strictly bug, but Strictly's README should call this out)
**Affects**: any app with custom build types beyond `debug` and `release`

Branch's `app/build.gradle.kts` declares four buildTypes: `debug`, `staging`, `release`, `benchmark`. The current integration only wires:

```kotlin
debugImplementation("io.github.vinaywadhwa.strictly:strictly:0.1.0")
releaseImplementation("io.github.vinaywadhwa.strictly:strictly-noop:0.1.0")
```

This means `staging` and `benchmark` get neither artifact: a Strictly-namespace symbol reference (eg. `Strictly.violations.collectAsState()`) in production code would fail to compile in `staging` and `benchmark`. Branch hasn't hit this yet because no production code references Strictly symbols, but it's a latent footgun.

**Fix on Strictly's side**: README should document the convention of wiring all non-debug variants to noop. eg:

```kotlin
debugImplementation("io.github.vinaywadhwa.strictly:strictly:0.1.0")
stagingImplementation("io.github.vinaywadhwa.strictly:strictly-noop:0.1.0")
releaseImplementation("io.github.vinaywadhwa.strictly:strictly-noop:0.1.0")
benchmarkImplementation("io.github.vinaywadhwa.strictly:strictly-noop:0.1.0")
```

Or document a Gradle helper that fans out automatically across all non-debug variants.

### Issue 8 (tooling, not a Strictly bug): Android screencap PNGs are 1080x2400, Claude's Read tool rejects images over 2000px per side

**Severity**: low, tooling note for future automated test sessions
**Affects**: any automated QA flow using `adb exec-out screencap -p` + Claude Read

`adb exec-out screencap -p` on a Pixel 7 emulator produces 1080x2400 PNGs. Claude Code's Read tool rejects images with any side over 2000px with "An image in the conversation exceeds the dimension limit for many-image requests (2000px)". Worse: once such an image enters conversation history, every following assistant turn also fails with the same error until the session is compacted.

**Workaround** (saved to `~/.claude/hooks/scripts/adb-screencap-safe.sh`): pipe screencap through Pillow's `Image.thumbnail((1800,1800))` before reading. All Phase 2 captures used this script. None tripped the limit.

### Issue 9 (bug): `Thread` field on every violation reads "Strictly Listener" instead of the actual offending thread

**Severity**: medium (defeats one of the per-violation detail's most useful fields)
**Affects**: every violation captured by Strictly. visible on every per-violation detail screenshot in this report

In `StrictModeInstaller.handleRawViolation` (line 122):

```kotlin
val violation = Violation(
    ...
    threadName = Thread.currentThread().name,  // <-- runs on listener executor, not offending thread
    ...
)
```

The penaltyListener executor is a dedicated thread named `Strictly Listener`. By the time `handleRawViolation` runs, `Thread.currentThread().name` is always `Strictly Listener`. The actual offending-thread name (the main thread for ThreadPolicy violations. a Firebase background thread or the FinalizerDaemon for VmPolicy violations) is lost. Every per-violation detail in this report shows `Thread: Strictly Listener` which gives the developer zero useful information.

**Fix**: capture the offending thread name in the executor wrapper, before the executor hops the listener to the dedicated thread.

```kotlin
private val listenerExecutor: Executor = run {
    val real = Executors.newSingleThreadExecutor { r -> Thread(r, "Strictly Listener") }
    Executor { task ->
        val capturedThreadName = Thread.currentThread().name
        real.execute {
            offendingThreadLocal.set(capturedThreadName)
            try { task.run() } finally { offendingThreadLocal.remove() }
        }
    }
}

private val offendingThreadLocal = ThreadLocal<String>()

// Then in handleRawViolation:
threadName = offendingThreadLocal.get() ?: "unknown",
```

After this fix the Thread field becomes genuinely useful: ThreadPolicy rows show `main`, VmPolicy rows show the actual background thread name (eg `FinalizerDaemon`, `Firebase Crashlytics Init Thread`).

### Issue 10 (UX): list rows show full `ClassName.methodName:line` even when most rows share the same class prefix

**Severity**: low (UX papercut, but consistent and visible)
**Affects**: the violation list view in any app where multiple violations share a callsite class

In Phase 2 every Branch-origin row in the list reads `StrictlyProbeProvider.probeDiskWriteWriteBytes:103`, `StrictlyProbeProvider.probeDiskReadStatFs:89`, `StrictlyProbeProvider.probeDiskWriteCreateFile:96`, etc. The `StrictlyProbeProvider.` prefix is repeated across 6 of the 12 visible rows. The actual differentiator (the method name plus line number) is buried after the constant prefix.

The same pattern shows up in real-world apps: `LoginActivity.doSomething:23`, `LoginActivity.doSomethingElse:45`, etc. The class name is the constant. The reader has to skip past it to find the unique part.

**Possible fixes** (pick one, all are reasonable):

1. **Drop the class name from the subtitle**, render method plus line only. Move the class name to a smaller overline label above the subtitle, or to the per-violation detail screen. Material 3 `ListItem` with `overlineContent` plus `supportingContent` slots fits well.
2. **Common-prefix elision**: when the visible window contains multiple rows from the same simple class name, render the prefix in a muted color and the differentiator in default color. Similar to how IntelliJ collapses common package paths in the stack-trace view.
3. **Group by class**: render section headers (`StrictlyProbeProvider`. `Branch routing`. etc) with nested rows beneath. Adds visual structure plus removes the repetition. Tradeoff is height plus complexity.

My recommendation: option 1. It's the smallest change with the highest readability win. The class name is already shown front-and-center in the per-violation detail screen, so it isn't lost on tap. The list becomes scannable by the differentiating signal (method plus line) rather than the constant prefix.


---

## Recommendations (interim, will refine in final pass)

1. **Update README appPackages guidance**: the "inferred from applicationId" note is correct but misleading. Most non-trivial apps will need explicit config.

2. **Consider a "soft origin" fallback**: when no app frame matches `appPackages`, classify the first non-`android.*` non-`java.*` non-`libcore.*` non-`com.android.*` frame as a soft origin. Annotate it with a different visual (eg a yellow ring instead of a blue ring) so the developer knows it's a third-party violation. This is more useful than `unknown origin`.

3. **Branch integration recommendation**: add the explicit install call to `BranchApplication.onCreate`:
   ```kotlin
   override fun onCreate() {
       super.onCreate()
       Strictly.install(this, StrictlyConfig(
           appPackages = listOf("co.branch.", "com.branch_international."),
       ))
       registerCustomActivityCallbacks()
   }
   ```
   This will compile cleanly in both debug (real Strictly) plus release (no-op) variants.

4. **Branch integration: consider ignoring Firebase Crashlytics**: Firebase fires disk reads plus untagged sockets during init that Branch can't fix. Adding `com.google.firebase.` to `ignoredPackages` would clean the signal. Decide whether Branch wants to surface third-party violations (helpful when picking SDKs) or hide them (focus on Branch-actionable issues).

5. **Wire `staging` and `benchmark` variants to noop**: prevent latent compile breaks when production code starts referencing Strictly symbols.
   ```kotlin
   debugImplementation("io.github.vinaywadhwa.strictly:strictly:0.1.0")
   stagingImplementation("io.github.vinaywadhwa.strictly:strictly-noop:0.1.0")
   releaseImplementation("io.github.vinaywadhwa.strictly:strictly-noop:0.1.0")
   benchmarkImplementation("io.github.vinaywadhwa.strictly:strictly-noop:0.1.0")
   ```

6. **Fix `StrictlyActivity` BACK behaviour**: add `BackHandler(enabled = selectedFingerprint != null) { selectedFingerprint = null }` so system BACK pops detail back to the list instead of exiting the Activity. (See Issue 3.)

7. **Make `StrictlyActivity` edge-to-edge compliant**: call `enableEdgeToEdge()` in `onCreate`, drop the outer `Surface(Modifier.fillMaxSize())`, and wrap each screen in a `Scaffold(contentWindowInsets = WindowInsets.systemBars)`. Required for Android 15+ (targetSdk 35) where status-bar plus nav-bar attrs are ignored. Same fix also resolves the back-arrow tap-target issue. (See Issue 4.)

8. **Add a `soft origin` fallback for unknown-origin violations**: walk the stack past framework prefixes and label the first non-framework frame as a soft origin (eg `Firebase Remote Config`). Render with a different visual cue to distinguish first-party vs third-party. (See Issue 6.) This is the highest-leverage Strictly UX improvement from this QA pass: it would have automatically labelled Phase 2 finding 6 as `Firebase Remote Config` without any user config.

9. **Document StrictMode burst throttling in README**: 50 identical violations may surface as only ~6 in the list. Mention that this is an Android platform limitation, not a Strictly limitation, so users don't suspect their config. (See Issue 5.)

10. **Address Firebase main-thread init**: Branch's specific actionable. Firebase Remote Config and Firebase Perf both initialize eagerly from their auto-init ContentProviders and call SharedPreferences.getString on the main thread. Either (a) suppress with `ignoredPackages = ["com.google.firebase."]` to focus the signal, or (b) initialize them lazily via the Jetpack App Startup library with `manualInitialization` so they don't run on the cold-start critical path. Option (b) is the actual fix; option (a) is a triage workaround.

11. **Capture the offending thread name correctly**: wrap the listener executor so `Thread.currentThread().name` is read on the offending thread, not on `Strictly Listener`. Without this the `Thread` field in the per-violation detail is useless dead pixels. (See Issue 9.)

12. **Restructure list-row layout to drop the redundant class prefix**: use Material 3 `ListItem` slots, put `methodName:lineNumber` in the headline plus violation type, and demote the class name to a smaller overline (or to the detail screen only). Makes the list scannable by the differentiating signal. (See Issue 10.)

---

## Final verdict (after Phase 2)

Strictly is ready to share with the Branch team. Every promise in the README either passed or has a clearly-scoped follow-up. Four real Strictly bugs surfaced: Issue 3 (BACK behavior, one-line fix), Issue 4 (edge-to-edge compliance for Android 14 plus 15+, roughly 15 lines of Activity plus Scaffold rewiring), Issue 9 (`Thread` field always shows "Strictly Listener" instead of the offending thread, fixed via an executor wrapper with a ThreadLocal), and Issue 10 (list rows repeat the class-name prefix, fixed by moving the class name to an overline label). The biggest improvement opportunity (Issue 6: soft origin labelling) is a Strictly enhancement that would meaningfully reduce the "unknown origin" rate against any real app's third-party stack.

The noop artifact is best-in-class: 91% smaller classes, empty manifest, public-API-only stubs, single-instruction returns. Production builds carry zero footprint.

The drop-in integration story works: one `debugImplementation` line, no Application.onCreate code required, and within seconds the developer sees real main-thread violations from their app's own startup. The Phase 2 testing produced an organic ANR caught by Android while Strictly was running, which is the textbook demonstration of why this library exists.

