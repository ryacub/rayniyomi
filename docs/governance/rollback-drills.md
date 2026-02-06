# Rollback Drill Runbook

## Purpose
Prove rollback readiness for high-risk (`P0`/`T3`) changes.

## Cadence
- Run at least one rollback drill per quarter.
- Run an additional drill before major fork identity releases.

## Drill Steps
1. Select a recently merged high-risk change.
2. Simulate incident trigger and rollback decision.
3. Execute documented rollback path.
4. Validate service/app behavior after rollback.
5. Record time-to-detect and time-to-restore.

## Outputs
- What worked
- What failed
- Missing automation/checks
- Follow-up ticket(s) with owners and due dates
