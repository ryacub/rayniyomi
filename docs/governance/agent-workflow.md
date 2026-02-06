# Agent Workflow Policy

This repository follows ticket-driven delivery with explicit verification and review gates.

## Required Flow
1. Pick one ticket and verify dependencies.
2. Create a branch: `codex/<ticket-id>-<short-slug>`.
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

Smoke ticket execution validated on 2026-02-06.
