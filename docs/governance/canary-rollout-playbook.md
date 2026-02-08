# Canary Rollout Playbook

This document provides procedures for safe canary releases and quick rollbacks of the Rayniyomi Android application.

## Canary Rollout Checklist

### Pre-Rollout Checks

Before initiating a canary rollout, ensure the following are completed:

- [ ] **Build Verification**
  - Release APK built successfully
  - ProGuard/R8 optimization applied without errors
  - APK signature verified
  - All automated tests passing

- [ ] **Version Information**
  - Version code incremented correctly
  - Version name follows semantic versioning
  - Release notes drafted and reviewed

- [ ] **Monitoring Setup**
  - Crash reporting enabled (if configured)
  - Analytics tracking verified (if enabled)
  - Release monitoring dashboard prepared
  - Rollback procedures reviewed and tested

- [ ] **Communication**
  - Team notified of canary start time
  - Support channels prepared for user feedback
  - Rollback decision-makers identified

### Canary Rollout Stages

Execute rollout in stages with monitoring between each phase:

#### Stage 1: 1% Rollout (Initial Canary)
- **Duration:** 24 hours minimum
- **Target:** ~1% of user base
- **Success Criteria:**
  - Crash rate < baseline + 10%
  - No critical bugs reported
  - App launch success rate > 99%

#### Stage 2: 10% Rollout
- **Duration:** 48 hours minimum
- **Target:** ~10% of user base
- **Success Criteria:**
  - Crash rate stable or decreasing
  - No P0/P1 bugs reported
  - User ratings maintained or improved

#### Stage 3: 50% Rollout
- **Duration:** 48-72 hours
- **Target:** ~50% of user base
- **Success Criteria:**
  - All SLOs met
  - No blocking issues
  - Positive user feedback trend

#### Stage 4: 100% Rollout (Full Release)
- **Target:** All users
- **Action:** Promote to production
- **Post-Release:** Continue monitoring for 7 days

### Monitoring During Rollout

Monitor these metrics continuously during each stage:

**Critical Metrics:**
- Crash rate (crashes per user session)
- ANR (Application Not Responding) rate
- App launch time
- Memory usage (average and p95)
- Network error rates

**User Experience Metrics:**
- Manga/anime loading time
- Reader performance (frame drops)
- Player startup time
- Search response time

**Business Metrics:**
- Daily active users (DAU)
- User retention
- Feature adoption rates
- App store ratings

**Alert Thresholds:**
- Crash rate increase > 50%
- ANR rate increase > 30%
- App launch failures > 1%
- Critical path errors > 5%

## Rollback Playbook

### When to Rollback

Initiate immediate rollback if any of the following occur:

**P0 Triggers (Immediate Rollback):**
- Crash rate spike > 100% of baseline
- App fails to launch for significant user segment
- Data loss or corruption reported
- Security vulnerability discovered
- ANR rate > 5% of sessions

**P1 Triggers (Evaluate Rollback within 2 hours):**
- Crash rate increase 50-100% of baseline
- Critical feature completely broken
- Performance regression > 50%
- Multiple high-severity bugs reported

**P2 Triggers (Monitor and Decide):**
- Minor feature regression
- UI/UX issues
- Performance degradation < 50%
- Single high-severity bug with workaround

### How to Rollback

#### Google Play Console Rollback

1. **Halt Rollout:**
   ```
   Navigate to: Google Play Console > Release > Production
   Click: "Halt rollout" button
   Confirm halt
   ```

2. **Rollback to Previous Version:**
   ```
   Navigate to: Release > Production > Previous releases
   Select: Last stable version
   Click: "Restore this release"
   Set rollout: 100% immediately
   Confirm rollback
   ```

3. **Verify Rollback:**
   - Check version code in Play Console matches previous stable
   - Verify rollout percentage updated
   - Confirm users can download previous version

#### Local Build Rollback (Development/Testing)

If testing locally or in internal tracks:

```bash
# Ensure you're on the previous stable branch
git checkout main
git log --oneline -5  # Verify you're at the stable commit

# Rebuild the previous version
./gradlew assembleRelease

# Tag the rollback for tracking
git tag -a v0.18.1.1-rollback -m "Rollback from v0.18.1.2 due to [issue]"
git push origin v0.18.1.1-rollback
```

### Post-Rollback Actions

After executing a rollback:

1. **Immediate Actions (Within 30 minutes):**
   - [ ] Verify rollback completed successfully
   - [ ] Monitor crash rate returns to baseline
   - [ ] Post announcement in support channels
   - [ ] Update status page if applicable

2. **Short-Term Actions (Within 24 hours):**
   - [ ] Conduct incident post-mortem
   - [ ] Document root cause analysis
   - [ ] Create GitHub issues for identified bugs
   - [ ] Update testing procedures to catch similar issues
   - [ ] Communicate timeline for fix to users

3. **Long-Term Actions (Within 1 week):**
   - [ ] Implement fixes for identified issues
   - [ ] Add regression tests
   - [ ] Review and update monitoring alerts
   - [ ] Update deployment checklist based on learnings
   - [ ] Schedule re-release when fixes verified

## Communication Templates

### Canary Start Announcement

```
🚀 Canary rollout started for Rayniyomi v[VERSION]

Stage: [1%/10%/50%/100%]
Start time: [TIMESTAMP]
Monitoring: [DASHBOARD_LINK]
Expected completion: [TIMESTAMP]

Team: Monitor alerts and user feedback closely.
```

### Rollback Notification

```
⚠️ Rollback initiated for Rayniyomi v[VERSION]

Trigger: [P0/P1 issue description]
Action: Rolling back to v[PREVIOUS_VERSION]
Status: In progress
ETA for rollback completion: [TIMESTAMP]

Post-mortem scheduled for: [DATE/TIME]
```

### Rollback Complete Notification

```
✅ Rollback completed for Rayniyomi v[VERSION]

Current version: v[PREVIOUS_VERSION]
Rollback duration: [DURATION]
Impact: [USER_COUNT] users affected

Next steps:
- Post-mortem: [DATE/TIME]
- Fix timeline: [ESTIMATE]
- Re-release target: [DATE]
```

## References

- [Release Monitoring Dashboard Updates](../docs/governance/release-monitoring-dashboard.md)
- [Agent Workflow](../docs/governance/agent-workflow.md)
- [Branch Protection](../docs/governance/branch-protection.md)

## Revision History

- 2026-02-08: Initial version (R31)
