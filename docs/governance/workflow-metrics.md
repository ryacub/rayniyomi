# Workflow Metrics & Retrospective Policy

To maintain high velocity and quality in the `rayniyomi` fork, we track key performance indicators (KPIs) and conduct weekly retrospectives.

## Key Metrics

### 1. Velocity & Efficiency
- **Cycle Time**: Time from branch creation (ticket start) to PR merge.
  - *Target*: < 2 days for T1/T2 tickets.
- **WIP Stability**: Number of open branches per agent.
  - *Constraint*: Max 2 (Enforced by R44).

### 2. Quality & Stability
- **Escaped Defects**: Number of failures or regressions discovered after PR merge.
  - *Goal*: 0 for P0/P1 scope.
- **CI Health**: Percentage of successful vs. failed CI runs on `main`.
  - *Target*: > 98%.
- **Flaky Checks**: Number of non-deterministic CI failures.

### 3. Compliance
- **Governance Bypass Rate**: Number of PRs merged without full DoD checklist or required validation.
  - *Goal*: 0%.

## Weekly Retrospective
Every week (Friday), a summary of these metrics is generated to:
- Identify process bottlenecks.
- Address recurring flaky tests.
- Update governance guardrails (Lane C tickets).

## Data Collection
Metrics are collected directly from:
- GitHub Actions run history.
- PR merge timestamps.
- Remediation board history.
