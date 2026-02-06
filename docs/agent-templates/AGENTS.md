# Local Agent Rules (Repo Scope)

These rules apply to all agent work in this folder.

## Required Process
1. Choose exactly one ticket before editing code.
2. Create/use a dedicated branch: `r<ticket-id>/<slug>` or `codex/<ticket-id>-<slug>`.
3. Follow the workflow in `.local/LLM_DELIVERY_PLAYBOOK.md`.
4. Implement only in-ticket scope (no unrelated refactors).
5. Run verification appropriate to the ticket risk tier.
6. Perform self-review before commit.
7. Commit with ticket prefix: `<ticket-id>: <summary>`.
8. Open/update PR with compliant title and fully updated description.
9. Update the local sprint board notes after work.

## Hard Stops
- If ticket ID, acceptance criteria, or dependencies are unclear: stop and clarify.
- If unexpected repo changes appear: stop and report before continuing.
- If verification cannot be run: document exactly what was skipped and why.

## Quality Gates
- No new `runBlocking` in UI-thread callbacks.
- No new top-level `GlobalScope` usage.
- No multi-ticket commit unless explicitly planned.
- PR title must follow Conventional Commits and include ticket ID: `type: summary (R123)`ption must include scope, verification, risk, and rollback notes.

## Priority Enforcement
- `P0` fork-compliance tickets are blocking for any external fork distribution.
- `P0`/`T3` tickets must include rollback notes.

## Work-in-Progress Limits (R44)
- One active ticket per agent at a time.
- Max two open branches per human owner.
- Do not start new `P0` or `T3` tickets until current is in `review` or `done`.

## High-Conflict Files (Single Owner)
The following files require coordination with owners to prevent merge conflicts:
- `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/manga/MangaScreenModel.kt`

**Rule:** Coordinate with file owner before modifying. If owner unavailable, create follow-up ticket.

## Source of Truth
- Playbook: `.local/LLM_DELIVERY_PLAYBOOK.md`
- Sprint board: `.local/remediation-sprint-board.md`
- Agent Guidelines: `CLAUDE.md`, `GEMINI.md`
