## Ticket
- ID: R____
- Priority: P0 | P1 | P2
- Risk Tier: T1 | T2 | T3
- GitHub Issue: Closes #____ (required; use `Closes`, `Fixes`, or `Resolves`)

## Objective
- What problem does this PR solve?

## Scope
- What is included in this PR?

## Non-goals
- What is intentionally not included?

## Files Changed
- List high-level areas changed.

## Verification
- Commands run:
  - `...`
- Results:
  - ...
- Not tested:
  - ...

## Risk
- Regression risks and mitigations.

## SLO Impact
- Which runtime SLOs are affected (if any), and expected impact.

## Rollback
- How to safely disable or revert this change.

## Release Notes
- User-facing summary for changelog.
- If no user-facing impact, explain why.

## Checklist
- [ ] Naming conventions followed (use "Rayniyomi" in user-facing text, see [naming conventions](../docs/governance/naming-conventions.md))
- [ ] Branch rebased on latest main and verification re-run (see [rebase policy](../docs/governance/agent-workflow.md#rebase-before-merge-policy-r45))

## Definition of Done (DoD)
- [ ] Acceptance criteria met and non-goals respected
- [ ] Verification matrix completed (Risk Tier: T__)
- [ ] Self-review completed
- [ ] Rebased on latest `main` and revalidated (mandatory for merge)
- [ ] Sprint board updated (branch, commit, checks, PR link)
- [ ] Release notes drafted (if applicable)

## Fork Compliance Checklist (for new forks only)
If you are creating a new fork of this project, ensure the following:
- [ ] **App Identity**: Changed app name from "Aniyomi" to your fork name
- [ ] **App Icon**: Replaced launcher icon assets with fork-specific icons
- [ ] **Application ID**: Changed `applicationId` in `app/build.gradle.kts` from `xyz.rayniyomi` to your unique ID
- [ ] **Update Checker**: Configured `AppUpdateChecker.kt` to point to your fork's repository
- [ ] **Analytics**: Either disabled Firebase Analytics or configured with your own `google-services.json`
- [ ] **Crash Reporting**: Either disabled ACRA crash reporting or configured with your own endpoint credentials

See [CONTRIBUTING.md](../CONTRIBUTING.md) Forks section for details.
