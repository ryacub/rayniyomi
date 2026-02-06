# Dependency and CVE Policy

## Update Cadence
- Weekly: review Renovate PRs.
- Monthly: complete dependency hygiene pass.
- Immediate: patch critical CVEs (CVSS >= 9.0).

## CVE Triage SLA
- Critical: action within 24 hours.
- High: action within 3 business days.
- Medium/Low: action in next scheduled dependency cycle.

## Required Triage Notes
For each dependency change:
- Affected component(s)
- Security impact (CVE link if any)
- Upgrade risk
- Rollback method

## Rules
- Prefer minimal version jumps for risk reduction.
- Separate security updates from feature refactors when possible.
- Every dependency PR must reference a dependency update ticket.
