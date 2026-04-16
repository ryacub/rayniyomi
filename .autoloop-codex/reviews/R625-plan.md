# R625 / #658 Plan

## Previous Retro Reference
- `.autoloop-codex/reviews/R625-retro.md`

## Process Delta
- Enforce the skill-local autoloop runner path and pass `path-check` before any gate artifact generation.
- Keep #658 internal/core-scoped with compatibility adapters to preserve #659 dependency boundary.

## Summary
Implement #658 as a single-writer queue actor/reducer refactor with deterministic race-proofing and T3 rollback notes, while preserving external behavior and leaving broader caller cleanup to #659.

## Implementation Changes
- Add queue command/effect/reducer/actor contracts in download core.
- Route anime/manga queue mutations through manager-level suspend command entrypoints.
- Keep compatibility adapters for existing caller surfaces to avoid scope bleed into #659.
- Define explicit command semantics: idempotency, ordering, duplicate handling, missing-ID no-op.
- Define lifecycle/failure model: supervised manager-owned actor; per-command failures do not kill actor.
- Preserve invariants: no queue resurrection; start behavior parity.
- Add T3 rollback notes in PR (revert path + recovery verification).

## Public Interfaces / Type Changes
- Internal additions: `DownloadQueueCommand`, `DownloadQueueEffect`, `DownloadQueueReducer`, `DownloadQueueActor`.
- Internal manager API: suspend command entrypoints plus compatibility adapters.
- No user-facing API or UX changes.

## Test Plan
- Reducer transition/effect matrix tests.
- Deterministic barrier-based race tests (no sleep timing).
- Parity tests for downloader start/stop behavior.
- Verification commands:
  - `./gradlew :app:testStableDebugUnitTest --tests "*DownloadQueue*"`
  - `./gradlew :app:testStableDebugUnitTest --tests "*AnimeDownloadManagerTest*"`
  - `./gradlew :app:testStableDebugUnitTest --tests "*MangaDownloadManagerTest*"`
  - `./gradlew spotlessCheck`
  - `./gradlew :app:compileDebugKotlin`

## Assumptions
- #659 remains follow-up for broader external caller/API cleanup.
- High-conflict file owner coordination is a hard stop.
