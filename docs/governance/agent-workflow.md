# Agent Workflow Policy

This repository follows ticket-driven delivery with explicit verification and review gates.

## Required Flow
<<<<<<< HEAD
1. Pick one ticket and verify dependencies (via GitHub Issues).
2. Create a branch: `codex/<ticket-id>-<short-slug>`.
=======
1. Pick one ticket and verify dependencies.
2. Create a branch: `r<ticket-id>/<short-slug>` or `codex/<ticket-id>-<short-slug>`.
>>>>>>> e70d68b7b (docs: update agent workflows and ensure issue template exists (R42))
3. Implement only in-scope changes.
4. Run verification by risk tier (`T1`/`T2`/`T3`).
5. Run self-review.
6. Commit with ticket-prefixed message.
7. Update PR title/description and link to GitHub Issue.

## PR Requirements
- Title format: `<type>: <summary> (R123)` (following Conventional Commits, e.g. `feat: add feature (R123)`)
- Body must include: Ticket, Objective, Scope, Non-goals, Files Changed, Verification, Risk, Rollback, Release Notes.

## High-Risk Rules
For `P0` or `T3` work:
- Labels required: `breaking-change`, `rollback-tested`
- **Rollback section must describe exact reversal steps.** This include:
  - CLI commands to revert migrations (if any).
  - Feature flag toggle names.
  - Manual cleanup steps for cache or local storage.
  - Revert commit reference if rollback is non-trivial.

## Work-in-Progress Limits (R44)
To maintain velocity and prevent context-switching overhead:
- **One active ticket per agent at a time.** Finish current work before starting new tickets.
- **Maximum two open branches per human owner.** Clean up merged branches promptly.
- **Do not start a new `P0` or `T3` ticket until current one is in `review` or `done`.** High-priority/high-risk work requires focused attention.

## Branch Ownership & High-Conflict Files
The following files have single-owner constraints to prevent merge conflicts:
- `PlayerViewModel.kt` - @player-owner
- `ReaderViewModel.kt` - @reader-owner  
- `AnimeScreenModel.kt` - @anime-owner
- `MangaScreenModel.kt` - @manga-owner

**Rules:**
- Coordinate with file owner before modifying high-conflict files
- If owner is unavailable, create follow-up ticket rather than bypassing
- Conflicts on these files require explicit human review before merge

## Rebase & Revalidation (R45)
To ensure code quality and prevent regressions in a fast-moving fork:
- **PRs must be rebased on latest `main` immediately before merging.**
- **Post-rebase revalidation is mandatory.** If conflicts were resolved, re-run all tests and verification steps.
- **Do not merge PRs with stale merge status.** If `main` has moved significantly since the last CI run, a re-check is required.

## Definition of Done
- Acceptance criteria met.
- Verification evidence captured.
- PR metadata complete and accurate.
- Risks + rollback documented.

## Failure Learning Loop (R47)
All critical failures (broken main, failed releases, process bypasses) require a **Failure Analysis**:
1. Open a **Failure Analysis** ticket using the provided template.
2. Identify the root cause and missing guardrail.
3. Create follow-up remediation tickets (Lane C) before closing the analysis.
4. See [Failure Analysis Policy](failure-analysis-policy.md) for details.

Smoke ticket execution validated on 2026-02-06.
