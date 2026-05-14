# R454 — UI-Boundary Immutability for Compose Stability

## Summary
Implement #454 by making discover/enrichment state Compose-stable at the UI boundary using immutable collection types and targeted stability annotations. Scope is UI-boundary only; no broad domain-layer Compose coupling.

Previous retro reference: `.autoloop-codex/reviews/R458-retro.md`.

## Behavior Parity Track
- Preserve discover/enrichment user-visible behavior; no logic or navigation changes.
- Preserve entry interfaces used by manga/anime screens unless minimal projection is required for compile-safe stability.
- Keep upstream-owned files (`Anime.kt`, `Manga.kt`) untouched.

## Structure Quality Track
- Convert Discover state list exposure to immutable collection type.
- Apply targeted stability annotation to discover/enrichment state classes based on field contracts.
- Convert assignment points to immutable collection writes.

## State Ownership + Mutation Boundary
- State owner: `DiscoverScreenModel` / `EntryEnrichmentScreenModel`.
- Event emitters: UI refresh intents and coordinator observation streams.
- Single mutation boundary: `mutableState.update { ... }` in each screen model.

## Reusable Component Decision
- No component extraction in this ticket.
- Rationale: this is a state contract/perf-stability patch; UI component reuse work is orthogonal and deferred.

## Non-Functional Acceptance Criteria
- i18n/text: no new user-facing strings introduced.
- Accessibility: semantics/focus behavior unchanged.
- Resilience: empty/error/loading paths remain intact; no nullability regressions.

## Coupling Budget
- Allowed: Compose runtime annotations, immutable collections in UI state.
- Prohibited: moving Compose annotations deeply into unrelated domain modules or refactoring unrelated model layers.

## Design Review Questions
- Is mutation centralized at `mutableState.update`? Yes.
- Are composables stateless/reusable? Unchanged.
- Are nullable/force-unwrap risks removed or justified? No new force unwraps.
- Are unsupported states safe/observable? Yes, existing error/empty states preserved.

## Implementation Steps
1. Update discover state `items` from `List` to immutable collection type with persistent default.
2. Convert incoming discover list assignments using `toImmutableList()`.
3. Add stability annotation for discover state (`@Immutable`) once contract satisfied.
4. Evaluate enrichment state contract:
   - use `@Immutable` only if field contract is immutable/stable,
   - otherwise use `@Stable` with rationale in PR.
5. If required for stability correctness with minimal blast radius, add narrow UI projection only in entries-common package files (`EntryEnrichmentScreenModel.kt` + colocated new UI-only projection file). Do not alter domain model declarations or domain package APIs.

## Verification
- `./gradlew spotlessCheck`
- `./gradlew :app:compileDebugKotlin (fallback to :app:compileStableDebugKotlin if task name is ambiguous; document fallback in PR)`
- Static evidence in PR: changed state field types + annotation rationale.
- Manual follow-up note for Compose Inspector recomposition check on discover/enrichment flows.

## Review Artifact Contract
- Independent reviewer subagent artifacts for plan and implementation phases.
- Required JSON metadata fields:
  - `reviewer_type=independent_agent`
  - `review_generated_by=subagent`
  - non-empty `reviewer_agent_id`
  - non-empty `reviewer_model`

## Process Delta
- Added explicit UI-boundary immutability rule before annotation in response to prior overreliance on annotation-only stability assumptions.
- Added mandatory required-vs-optional evidence declaration in PR verification notes.

## Delivery + Closeout
- Update `CHANGELOG.md` with exactly one bullet for this ticket.
- Open PR with conventional title including `(R454)` and `Closes #454`.
- Merge after required checks are green.
- Update R-BOARD #71 and `.local/sprint-board-notes.md` with one-line process delta applied.
- Run autoloop closeout (`close` + `doctor --strict`) and cleanup worktree/branch.


## Behavior Smoke Matrix
- Discover: loading, success list render, empty state, refresh path, and error message path unchanged.
- Enrichment: loading, success recommendations render, empty/failure summary text, manual refresh announcements, and navigation wiring unchanged.

## Annotation Evidence Checklist
- For each annotated state class, list every field and why it satisfies immutable/stable contract.
- If `@Stable` is used instead of `@Immutable`, include rationale in PR verification section.
