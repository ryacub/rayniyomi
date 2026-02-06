# Agent Workflow Policy

This repository follows ticket-driven delivery with explicit verification and review gates.

## Required Flow
1. Pick one ticket and verify dependencies (via GitHub Issues).
2. Create a branch: `codex/<ticket-id>-<short-slug>`.
3. Implement only in-scope changes.
4. Run verification by risk tier (`T1`/`T2`/`T3`).
5. Run self-review.
6. Commit with ticket-prefixed message.
7. Update PR title/description and link to GitHub Issue.

## PR Requirements
- Title format: `<type>: <summary> (R123)` (matched against Conventional Commits)
- Body must include: Ticket, Objective, Scope, Non-goals, Files Changed, Verification, Risk, Rollback, Release Notes.

## High-Risk Rules
For `P0` or `T3` work:
- Labels required: `breaking-change`, `rollback-tested`
- Rollback section must describe exact reversal steps.

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

## Rebase-Before-Merge Policy (R45)

### Required Steps
1. **Fetch latest target branch** daily for active branches
   ```bash
   git fetch --prune && git rebase origin/main
   ```
2. **Rebase before final verification** - always rebase on latest main before marking PR ready
3. **Re-run verification matrix** after rebase based on ticket risk tier:
   - T1: targeted tests + lint
   - T2: targeted + module tests + sanity check
   - T3: full suite + regression checks

### Post-Rebase Revalidation Checklist
- [ ] Conflicts resolved without scope creep
- [ ] High-conflict files reviewed (if touched)
- [ ] PR title/description still accurate
- [ ] Verification re-run and passing
- [ ] No unexpected changes in diff

### Merge Conflict Response
- On conflict, pause new work and resolve first
- Keep resolution scoped (no unrelated refactors)
- Record in PR notes: conflicting files, resolution choice, behavior risks
- Re-run full verification matrix after resolution

## Definition of Done
- Acceptance criteria met.
- Verification evidence captured.
- PR metadata complete and accurate.
- Risks + rollback documented.

Smoke ticket execution validated on 2026-02-06.
