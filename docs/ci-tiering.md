# CI Tiering

Rayniyomi CI is split by the risk each check proves at review time. A PR check
should be required only when it catches merge-blocking risk quickly and
reliably. Expensive or runner-sensitive checks are removed unless they have a
clear maintainer-owned use case outside normal PR feedback.

## Required PR Gate

These checks are expected on normal pull requests:

| Check | Workflow | Reason |
| --- | --- | --- |
| `validate-pr` | `.github/workflows/pr_governance.yml` | Enforces title, body, risk, and issue-closing contract. |
| `gitleaks` | `.github/workflows/secret_scan.yml` | Blocks accidental secret exposure. |
| `Build app` | `.github/workflows/build_pull_request.yml` | Builds the stable APK and verifies 16 KB native alignment. |
| `Check code format` | `.github/workflows/build_pull_request.yml` | Keeps formatting drift out of review. |
| `Run unit tests` | `.github/workflows/build_pull_request.yml` | Catches JVM test regressions without emulator cost. |
| `Verify baseline profile task and artifact` | `.github/workflows/build_pull_request.yml` | Confirms baseline profile wiring and committed artifact presence. |
| `Branding guardrail` | `.github/workflows/build_pull_request.yml` | Prevents user-facing fork-brand regressions. |
| `Plugin Compatibility` | `.github/workflows/plugin_compatibility.yml` | Targeted PR gate for light novel plugin host and manifest changes. |

## Removed Emulator Gate

These hosted-emulator workflows were removed because they were slow, noisy, and
not required by branch protection. Keeping them as manual workflows would still
leave unowned CI surface area without a clear merge-blocking signal.

| Removed check | Former workflow | Rationale |
| --- | --- | --- |
| Reader parity gate | `.github/workflows/reader_parity_gate.yml` | The smoke path was not a real benchmark gate and repeatedly produced 20-40 minute hosted-emulator failures without a stable PR-time decision. |
| Theme instrumentation gate | `.github/workflows/theme_instrumentation_pr.yml` | The gate duplicated broad instrumentation coverage on an unreliable hosted-emulator path and was not a required PR check. |

The reader parity smoke is classified as a smoke/contract gate, not a benchmark.
It does not currently provide stable performance thresholds or a reliable
regression signal on hosted runners.

## Release Gate

Release-only checks remain on tag or release workflows:

| Check | Workflow | Reason |
| --- | --- | --- |
| Stable APK signing and release upload | `.github/workflows/build_push.yml` | Requires release secrets and only matters for `v*` tags. |
| Changelog finalization | `.github/workflows/build_push.yml` | Produces release notes and post-release main sync. |
| Plugin release signing, verification, compliance, and publish | `.github/workflows/plugin_release.yml` | Requires plugin release tags and signing/compliance material. |

## Removal And Follow-Up Decisions

Before R665, branch pushes triggered the full `CI` workflow for every branch via
`branches: '*'`. After R665, branch-push CI runs for `main` and `v*` tags only;
normal PR branches use PR workflows. Expected impact: branch pushes avoid a
duplicate release-style build, baseline-profile dry run, unit test pass, and APK
artifact upload.

Before R665, the PR build workflow ran Gradle-heavy jobs for docs-only,
workflow-only, and CI-regression-test-only PRs. After R665, a cheap classifier
skips SqlDelight bootstrap, formatting, app build, app unit tests, and baseline
profile verification when changed files are limited to `docs/**`, `.github/**`,
`scripts/tests/**`, or Markdown. Expected impact: non-Android PRs keep
governance, secret scanning, and branding checks without spending runner time on
Android compilation.

Before R665, reader parity and theme instrumentation ran automatically on PR path
matches. After R665, both workflows are removed. Expected impact: ordinary
reader/theme PR iteration avoids 20-40 minute hosted-emulator jobs and stale
manual workflow surface area.

The SqlDelight bootstrap workaround is tracked separately by R663 / #724. R665
does not duplicate that dependency replacement; this document treats the
bootstrap as stale plumbing to remove through that ticket or a follow-up if R663
does not land.
