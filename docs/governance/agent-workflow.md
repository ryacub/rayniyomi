# Agent Workflow Policy

This repository follows ticket-driven delivery with explicit verification and review gates.

## Branch Naming Convention

**Format:** `<agent-prefix>/<ticket-id>-<short-slug>`

- `<agent-prefix>`: Identifier for the agent/developer (e.g., `codex`, `claude`, `dev`)
- `<ticket-id>`: Ticket identifier from sprint board (e.g., `R33`, `R34`)
- `<short-slug>`: Brief description (2-4 words, kebab-case)

**Examples:**
- `codex/r33-rename-program`
- `claude/r35-applicationid-update`
- `dev/r50-download-queue-fix`

**Current default:** `codex/<ticket-id>-<short-slug>` (for LLM/agent work)

## Required Flow
1. Pick one ticket and verify dependencies.
2. Create a branch following the naming convention above.
3. Implement only in-scope changes.
4. Run verification by risk tier (`T1`/`T2`/`T3`).
5. Run self-review.
6. Commit with ticket-prefixed message.
7. Update PR title/description and sprint state.

## PR Requirements
- Title format: `[R123] short imperative summary`
- Body must include: Ticket, Objective, Scope, Non-goals, Files Changed, Verification, Risk, Rollback, Release Notes.

## High-Risk Rules
For `P0` or `T3` work:
- Labels required: `breaking-change`, `rollback-tested`
- Rollback section must describe exact reversal steps.

## Definition of Done
- Acceptance criteria met.
- Verification evidence captured.
- PR metadata complete and accurate.
- Risks + rollback documented.

## Operational Learnings
- Treat required status check names as stable contracts.
- If workflow/job names change, update branch protection required contexts in the same change.
- Preferred required contexts on `main`: `Build app`, `gitleaks`, `validate-pr`.

## Fork CI Compatibility
- Keep fork-safe defaults for PR checks.
- Upstream-only checks (for example dependency review with unavailable security features on forks) must be conditionally gated, not hard-failing fork PRs.

## Merge-Block Runbook
1. Inspect PR checks: `gh pr checks <pr>`.
2. Inspect required contexts: `gh api repos/<owner>/<repo>/branches/main/protection/required_status_checks`.
3. Compare emitted check names vs required contexts exactly (case-sensitive).
4. If contexts mismatch, either:
   - Update workflow/job names to match required contexts, or
   - Update required contexts to match current workflow/job names.
5. Push a no-op retrigger commit on PR branch after policy/workflow updates.
6. Merge only after required contexts are present and passing on the latest head SHA.

Smoke ticket execution validated on 2026-02-06.
