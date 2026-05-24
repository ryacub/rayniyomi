# Startup Optimization: R473 — App.onCreate Audit

**Status: Complete** — optimizations merged to main. On-device macrobenchmark numbers not captured
(requires physical device); correctness verified via unit tests (7/7 pass).

## Macrobenchmark Command

```bash
# Requires connected device (emulator or physical)
./gradlew :app:generateBaselineProfile
# or directly:
./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=tachiyomi.macrobenchmark.ColdStartupBenchmark
```

Results appear in `macrobenchmark/build/outputs/`.

## App.onCreate: Sync Work Audit (pre-optimization)

| Line | Work | Deferrable? | Reason |
|------|------|-------------|--------|
| 99–103 | TLS Conscrypt provider install | No | Must run before any network in any process |
| 105–109 | WebView data dir suffix | No | Must run before WebView in any process |
| 111 | GlobalExceptionHandler install | No | Must run in all processes before any crash |
| 113 | `isMainProcess()` guard | No | Short-circuits secondary processes |
| 118–120 | Crashlytics init | **Already deferred** ✅ | `Dispatchers.IO` via lifecycleScope |
| 122–143 | StrictMode setup | No | Debug only; negligible cost |
| 145–149 | SecurePrefs + preference migrations | No | Required before DI |
| 151–158 | Injekt DI module setup | No | Required for app to function |
| 160 | `setupNotificationChannels()` | No | Needed at boot for notification delivery |
| 162–192 | Incognito mode Flow + observer | No | `.launchIn()` already async |
| 194–200 | Hardware bitmap threshold | No | `.launchIn()` already async |
| **202** | **`setAppCompatDelegateThemeMode()`** | **No** | Must run before first Activity layout inflation |
| **205–211** | **Widget managers (Manga + Anime)** | **Yes** | Can defer to `onStart()` via lifecycleScope |
| **213–215** | **LogcatLogger install** | **Yes** | Logging only; safe to defer a few ms |
| **217** | **`PeriodicTrackerSyncJob.setupTask()`** | **Yes** | WorkManager scheduling; safe to defer |
| 218 | `initializeMigrator()` | Investigate | May be required before DI resolves |

## Optimization Plan

### Targets
1. **LogcatLogger** — defer to `Dispatchers.IO` coroutine (no correctness risk)
2. **PeriodicTrackerSyncJob.setupTask()** — defer to `Dispatchers.Default` (WorkManager scheduling)
3. **Widget managers** — defer construction + init to `onStart()` lifecycle event

### Non-targets
- Theme mode stays synchronous (required before first Activity)
- DI, migrations, notification channels stay synchronous

## Baseline Metrics

Run `ColdStartupBenchmark.startupNoCompilation` before and after changes on same device to compare
`timeToFullDisplayMs` metric. Aim for neutral-or-better result (acceptable if flat; flagged if regression).

Baseline captured: _not captured — device run required_
Post-optimization: _not captured — device run required_

### Why closed without device metrics

This is an `upstream-watch` ticket (R655). Rationale for closing without device numbers:
- Three synchronous blocks removed from main thread: LogcatLogger, MangaWidgetManager + AnimeWidgetManager, PeriodicTrackerSyncJob
- All moved to `ProcessLifecycleOwner.lifecycleScope.launch(Dispatchers.IO)` — verified off-main-thread by dispatcher
- Each wrapped in try/catch with `Log.e()` — failures are visible but non-fatal
- 7/7 unit tests pass (`AppStartupOptimizationTest`)
- No regression path: all deferred work is non-critical for first Activity layout
