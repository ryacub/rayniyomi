# Release Monitoring Dashboard Configuration

This document defines metrics, thresholds, and alert configurations for monitoring Rayniyomi releases.

## Overview

The release monitoring dashboard tracks critical quality metrics across releases to detect regressions early. Key areas monitored:

- **ANR (Application Not Responding)** rate
- **Crash rate** (fatal errors)
- **Performance metrics** (startup time, frame rate, memory)
- **Release-over-release deltas** for trend analysis

## Metrics Definitions

### ANR Rate

**Definition:** Percentage of user sessions experiencing ANR events.

**Calculation:**
```
ANR Rate = (Sessions with ANR / Total Sessions) × 100
```

**Baseline:** < 0.5% of sessions
**Alert Threshold:**
- Warning: > 0.5% absolute or +50% relative to previous release
- Critical: > 1.0% absolute or +100% relative to previous release

**Collection Points:**
- Activity lifecycle blocking (onCreate, onResume)
- BroadcastReceiver.onReceive blocking
- Input event processing delays > 5s

### Crash Rate

**Definition:** Percentage of user sessions experiencing fatal crashes.

**Calculation:**
```
Crash Rate = (Crashed Sessions / Total Sessions) × 100
```

**Baseline:** < 1% of sessions
**Alert Threshold:**
- Warning: > 1% absolute or +50% relative to previous release
- Critical: > 2% absolute or +100% relative to previous release

**Crash Categories:**
- **Native crashes:** JNI/native library failures (mpv, FFmpeg)
- **Kotlin crashes:** Uncaught exceptions in Kotlin code
- **OOM crashes:** Out-of-memory errors
- **Framework crashes:** Android framework errors

### Performance Metrics

#### App Startup Time

**Definition:** Time from app launch to first frame displayed.

**Calculation:**
```
Cold Start Time = Time from Process Start to First Frame
Warm Start Time = Time from Activity.onStart to First Frame
```

**Baseline:**
- Cold start: < 2 seconds (p95)
- Warm start: < 1 second (p95)

**Alert Threshold:**
- Warning: +20% relative to previous release
- Critical: +50% relative to previous release

#### Frame Rate (Jank)

**Definition:** Percentage of frames taking > 16ms to render (60fps target).

**Calculation:**
```
Jank Rate = (Slow Frames / Total Frames) × 100
```

**Baseline:** < 5% slow frames
**Alert Threshold:**
- Warning: > 5% absolute or +50% relative to previous release
- Critical: > 10% absolute or +100% relative to previous release

**Critical Paths Monitored:**
- Reader screen scrolling
- Player video playback
- Library grid scrolling
- Search results rendering

#### Memory Usage

**Definition:** Average and peak memory consumption per session.

**Metrics:**
- Average heap usage
- Peak heap usage
- Native memory usage
- OOM occurrence rate

**Baseline:**
- Average heap: < 200MB
- Peak heap: < 400MB
- OOM rate: < 0.1%

**Alert Threshold:**
- Warning: +30% relative to previous release
- Critical: +50% relative to previous release

### Release Delta Tracking

**Purpose:** Track metric changes between consecutive releases to detect regressions.

**Metrics:**
```yaml
release_delta:
  anr_rate:
    current_release: "0.18.1.2"
    previous_release: "0.18.1.1"
    current_value: 0.45
    previous_value: 0.40
    delta_absolute: +0.05
    delta_percentage: +12.5%
    threshold_exceeded: false

  crash_rate:
    current_release: "0.18.1.2"
    previous_release: "0.18.1.1"
    current_value: 0.82
    previous_value: 0.75
    delta_absolute: +0.07
    delta_percentage: +9.3%
    threshold_exceeded: false

  startup_time_p95:
    current_release: "0.18.1.2"
    previous_release: "0.18.1.1"
    current_value: 1850  # milliseconds
    previous_value: 1780
    delta_absolute: +70
    delta_percentage: +3.9%
    threshold_exceeded: false
```

## Dashboard Configuration

### Google Play Console (Primary)

If using Google Play Console for monitoring:

**Pre-Launch Report Metrics:**
- Stability rate (crashes + ANRs)
- Performance score
- Battery usage
- Security vulnerabilities

**Production Metrics:**
- Crash clusters (grouped by stack trace)
- ANR clusters
- User-perceived startup time
- User ratings and reviews

**Configuration:**
1. Navigate to: Play Console > Quality > Android vitals
2. Enable alerts for:
   - Crash rate > 1.09%
   - ANR rate > 0.47%
   - Startup time regression > 20%

### Firebase Crashlytics (Optional)

If Firebase is enabled (currently disabled in Rayniyomi):

```json
{
  "crashlytics": {
    "enabled": false,
    "ndk_enabled": false,
    "alerts": {
      "crash_rate": {
        "threshold_percentage": 1.0,
        "comparison": "7_days"
      },
      "velocity_alert": {
        "threshold": 5,
        "window_minutes": 60
      }
    }
  }
}
```

### Custom Dashboard (Grafana/Prometheus)

If implementing custom monitoring:

**Metrics to Export:**
```yaml
metrics:
  - name: app_anr_rate
    type: gauge
    unit: percentage
    labels: [version, device_type, android_version]

  - name: app_crash_rate
    type: gauge
    unit: percentage
    labels: [version, crash_type, device_type]

  - name: app_startup_time
    type: histogram
    unit: milliseconds
    buckets: [500, 1000, 1500, 2000, 3000, 5000]
    labels: [version, startup_type]

  - name: app_frame_time
    type: histogram
    unit: milliseconds
    buckets: [8, 16, 33, 50, 100]
    labels: [version, screen_type]

  - name: app_memory_usage
    type: gauge
    unit: megabytes
    labels: [version, memory_type]
```

**Example Grafana Dashboard JSON:**
```json
{
  "dashboard": {
    "title": "Rayniyomi Release Quality",
    "panels": [
      {
        "title": "ANR Rate by Release",
        "type": "graph",
        "targets": [
          {
            "expr": "app_anr_rate{version=~\".*\"}",
            "legendFormat": "{{version}}"
          }
        ],
        "alert": {
          "conditions": [
            {
              "evaluator": {
                "params": [0.5],
                "type": "gt"
              }
            }
          ]
        }
      },
      {
        "title": "Crash Rate by Type",
        "type": "graph",
        "targets": [
          {
            "expr": "app_crash_rate{version=\"$version\"}",
            "legendFormat": "{{crash_type}}"
          }
        ]
      },
      {
        "title": "Startup Time p95",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, app_startup_time{version=~\".*\"})",
            "legendFormat": "{{version}}"
          }
        ]
      }
    ]
  }
}
```

## Alert Configuration

### Alert Channels

Configure alert routing:

```yaml
alert_channels:
  - name: critical
    type: pagerduty
    recipients: [on_call_engineer]
    conditions: [anr_critical, crash_critical, oom_critical]

  - name: warning
    type: slack
    channel: "#rayniyomi-alerts"
    conditions: [anr_warning, crash_warning, perf_warning]

  - name: info
    type: email
    recipients: [dev_team@example.com]
    conditions: [release_deployed, canary_milestone]
```

### Alert Rules

**ANR Alert:**
```yaml
- alert: HighANRRate
  expr: app_anr_rate > 0.5
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "ANR rate above threshold for {{ $labels.version }}"
    description: "ANR rate is {{ $value }}% (threshold: 0.5%)"

- alert: CriticalANRRate
  expr: app_anr_rate > 1.0
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "CRITICAL: ANR rate for {{ $labels.version }}"
    description: "ANR rate is {{ $value }}% (threshold: 1.0%)"
```

**Crash Alert:**
```yaml
- alert: HighCrashRate
  expr: app_crash_rate > 1.0
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "Crash rate above threshold for {{ $labels.version }}"
    description: "Crash rate is {{ $value }}% (threshold: 1.0%)"

- alert: CriticalCrashRate
  expr: app_crash_rate > 2.0
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "CRITICAL: Crash rate for {{ $labels.version }}"
    description: "Crash rate is {{ $value }}% (threshold: 2.0%)"
```

**Performance Regression Alert:**
```yaml
- alert: StartupTimeRegression
  expr: |
    (
      app_startup_time{quantile="0.95", version="current"}
      - app_startup_time{quantile="0.95", version="previous"}
    ) / app_startup_time{quantile="0.95", version="previous"} > 0.2
  for: 10m
  labels:
    severity: warning
  annotations:
    summary: "Startup time regression detected"
    description: "p95 startup time increased by {{ $value | humanizePercentage }}"
```

## Implementation Notes

### For Rayniyomi (Current State)

Rayniyomi currently has Firebase Analytics and ACRA crash reporting **disabled** (see R37/R38 implementation). To use this dashboard configuration:

1. **Option 1: Enable Google Play Console monitoring (Recommended)**
   - No code changes needed
   - Uses built-in Android vitals
   - Configure alerts in Play Console

2. **Option 2: Self-hosted monitoring**
   - Implement custom metrics export
   - Set up Prometheus + Grafana
   - Use provided configuration templates

3. **Option 3: Third-party service**
   - Firebase Crashlytics (requires configuration)
   - Sentry (requires integration)
   - Bugsnag (requires integration)

### Data Collection Compliance

When implementing monitoring, ensure:

- User consent obtained per GDPR/privacy laws
- Personally identifiable information (PII) not collected
- Data retention policies defined and enforced
- Users can opt-out of telemetry

## Usage

### During Canary Rollout

1. **Before rollout:**
   - Record baseline metrics from current production release
   - Configure alert thresholds
   - Set up monitoring dashboard

2. **During rollout:**
   - Monitor dashboard continuously
   - Compare metrics against baseline
   - Trigger rollback if thresholds exceeded

3. **After rollout:**
   - Update baseline metrics for next release
   - Review anomalies and adjust thresholds
   - Document any incidents

### Post-Mortem Analysis

When investigating issues:

1. Check dashboard for metric spikes
2. Correlate timing with release rollout stages
3. Review crash clusters and ANR patterns
4. Identify affected device types/Android versions
5. Document findings and action items

## References

- [Canary Rollout Playbook](./canary-rollout-playbook.md)
- [Agent Workflow](./agent-workflow.md)
- Android Vitals: https://developer.android.com/topic/performance/vitals

## Revision History

- 2026-02-08: Initial version (R32)
