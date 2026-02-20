# Plugin Performance Budgets

Performance and battery budgets for the light novel plugin subsystem.

## Overview

The light novel plugin operates as a separate process and communicates with the host application via IPC. To prevent performance degradation and battery drain, we enforce strict budgets on plugin-related operations.

## Budget Categories

### Startup Contribution

**Budget:** 150 ms

**Scope:** Time added to cold-start by plugin initialization on the critical startup path.

**Rationale:** Cold-start latency directly impacts user-perceived app responsiveness. Plugin initialization must remain negligible (<150ms) to avoid degrading the startup experience.

**Measurement:** Use `System.nanoTime()` to measure the duration of plugin initialization code that runs on the main thread or blocks UI rendering.

**What counts:**
- Feature gate evaluation during onCreate()
- Plugin readiness checks that block UI

**What doesn't count:**
- Background plugin installation
- Lazy-loaded plugin features
- Post-startup plugin operations

---

### Manifest Fetch

**Budget:** 5,000 ms (5 seconds)

**Scope:** Remote manifest retrieval including network request, JSON parsing, and basic validation.

**Rationale:** Manifest fetches occur during plugin installation flows. A 5-second timeout prevents indefinite hangs while allowing for slow network conditions.

**Measurement:** Measure from network request start to successful JSON deserialization.

**Applies to:**
- Stable channel manifest (`STABLE_MANIFEST_URL`)
- Beta channel manifest (`BETA_MANIFEST_URL`)

---

### Plugin Install

**Budget:** 30,000 ms (30 seconds)

**Scope:** APK download, signature verification, and system installer handoff.

**Rationale:** Plugin installation is a rare operation (typically once per app install or update). A 30-second timeout accommodates large APKs (~5-10MB) on slow connections while preventing indefinite hangs.

**Measurement:** Measure from APK download start to successful system installer launch.

**Does NOT include:**
- Time spent in system package installer UI (user-controlled)
- Post-install handshake (separate budget)

---

### Feature Gate Check

**Budget:** 5 ms

**Scope:** Feature flag evaluation latency.

**Rationale:** Feature gate checks occur on hot paths (UI rendering, navigation). A 5ms budget ensures that flag evaluation remains negligible overhead.

**Measurement:** Measure `LightNovelFeatureGate.isEnabled()` execution time.

**Critical:** If feature gate checks exceed this budget, they will degrade UI responsiveness.

---

### Memory Overhead

**Budget:** 15 MB

**Scope:** Additional memory footprint when plugin is loaded.

**Rationale:** Plugin process and IPC infrastructure should remain lightweight. A 15MB budget prevents memory pressure on low-end devices.

**Measurement:** Measure PSS (Proportional Set Size) delta before and after plugin load.

**What counts:**
- Plugin process memory
- IPC binder buffers
- Shared memory regions

**Measurement tools:**
- `adb shell dumpsys meminfo <package>`
- Android Profiler (Memory view)

---

### EPUB Import

**Budget:** 10,000 ms (10 seconds)

**Scope:** Parsing and importing a single EPUB file via the plugin's content provider interface.

**Rationale:** EPUB files vary widely in size (1-50MB). A 10-second budget accommodates typical files while identifying outliers.

**Measurement:** Measure from content provider `openFile()` to successful metadata extraction.

**Note:** Larger EPUB files (>10MB) may legitimately exceed this budget. Track p95 latency to identify outliers rather than individual violations.

---

### Background Wakeups

**Budget:** 2 per hour

**Scope:** Maximum number of background wake events caused by the plugin.

**Rationale:** Background wakeups prevent the device from entering deep sleep states, draining battery. A 2/hour budget limits idle power impact.

**Measurement:** Monitor `AlarmManager` and `JobScheduler` events attributed to the plugin.

**What counts:**
- Scheduled background tasks
- Push notification processing
- Sync operations

**What doesn't count:**
- User-initiated operations (opening app, clicking notification)
- Foreground service work

**Measurement tools:**
- `adb shell dumpsys batterystats --history`
- Battery Historian

---

## Tracking and Alerting

### Implementation

Performance tracking is implemented via:

- **`PluginPerformanceBudgets`**: Defines budget constants
- **`PluginPerformanceTracker`**: Records operation durations and computes percentiles
- **`PluginTelemetry`**: Extended with optional `durationMs` parameter for duration tracking

### Ring Buffer

Performance samples are stored in ring buffers (max 100 samples per category). This provides a rolling window of recent performance without unbounded memory growth.

### Percentiles

We track p50, p95, and p99 percentiles:

- **p50 (median)**: Typical performance
- **p95**: Worst-case performance for 95% of operations
- **p99**: Tail latency (1 in 100 operations)

**Budget violations are triggered when p95 exceeds the configured budget.**

### Violation Alerts

When a budget violation occurs, `PluginPerformanceTracker` logs at WARN level:

```
PLUGIN_PERF_VIOLATION category=STARTUP budgetMs=150 actualP95Ms=200 sampleCount=100
```

### Monitoring in Production

**Debug builds:** All performance tracking is enabled by default.

**Release builds:** Performance tracking is gated behind `BuildConfig.DEBUG || ENABLE_PERF_TRACKING_IN_RELEASE`.

To enable in release builds for beta testing:

```kotlin
private const val ENABLE_PERF_TRACKING_IN_RELEASE = true
```

---

## Usage

### Recording Operation Duration

```kotlin
val tracker = PluginPerformanceTracker()

val startNanos = System.nanoTime()
// ... perform operation ...
val durationMs = (System.nanoTime() - startNanos) / 1_000_000

tracker.recordOperation(OperationCategory.STARTUP, durationMs)
```

### Checking for Violations

```kotlin
tracker.checkViolations(OperationCategory.STARTUP)?.let { violation ->
    logcat(LogPriority.WARN) {
        "Budget violation: category=${violation.category} " +
        "budgetMs=${violation.budgetMs} actualP95Ms=${violation.actualP95Ms}"
    }
}
```

### Querying Stats

```kotlin
val stats = tracker.getStats(OperationCategory.MANIFEST_FETCH)
if (stats != null) {
    logcat {
        "Manifest fetch: p50=${stats.p50}ms p95=${stats.p95}ms p99=${stats.p99}ms " +
        "samples=${stats.sampleCount}"
    }
}
```

---

## Testing

### Unit Tests

- `PluginPerformanceBudgetsTest`: Validates budget constants
- `PluginPerformanceTrackerTest`: Tests percentile computation, ring buffer, violation detection
- `PluginTelemetryTest`: Tests duration tracking integration

### Manual Testing

To verify budget compliance in practice:

1. Enable debug logging:
   ```
   adb logcat | grep PLUGIN_PERF
   ```

2. Trigger operations:
   - Cold start app → Check `STARTUP` category
   - Install plugin → Check `PLUGIN_INSTALL` category
   - Import EPUB → Check `EPUB_IMPORT` category

3. Check for violations in logcat output

---

## Revision History

- **2026-02-20** (R236-R): Initial budget definition and tracking implementation
