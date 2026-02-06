# ADR 0002: Fork Isolation for Updates and Telemetry

- Status: Accepted
- Date: 2026-02-06
- Decision owners: @rayyacub

## Context
Forks should not pollute upstream telemetry/crash services and should not point update logic to upstream release channels.

## Decision
Require fork-owned or disabled configurations for:
- App update checker endpoints/behavior
- Firebase analytics config
- ACRA crash reporting endpoint

## Consequences
- Positive outcomes:
  - Clean data ownership for the fork.
  - Reduced risk of cross-project contamination.
- Tradeoffs:
  - Additional setup and maintenance burden.
  - Need to manage fork credentials securely.

## Alternatives Considered
- Reuse upstream services temporarily.
- Disable all telemetry and crash reporting permanently.
