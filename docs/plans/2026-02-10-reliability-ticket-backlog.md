# Reliability Refactor Ticket Backlog (Phased)

Date: 2026-02-10  
Scope: ANR, thread safety, race conditions, coroutine lifecycle, memory leak hardening

## Phase 1: Critical Stabilization (P1)

### REL-001: Remove migration blocking from app startup
- Priority: P1
- Risk: ANR / cold-start freeze
- Problem: `MainActivity` blocks the main thread via `Migrator.awaitAndRelease()`.
- Files:
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt`
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/mihon/core/migration/Migrator.kt`
- Tasks:
  - Replace blocking wait with async startup gate.
  - Keep splash visible until migration completion or timeout fallback.
  - Ensure changelog logic still works with async migration result.
- Acceptance Criteria:
  - No `runBlocking` from startup UI path.
  - App remains responsive during migration.
  - Existing migration + changelog behavior preserved.

### REL-002: Fix PlayerActivity global uncaught exception handler leak
- Priority: P1
- Risk: Activity memory leak + crash handler corruption
- Problem: `PlayerActivity` sets process-global default uncaught exception handler and never restores it.
- Files:
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt`
- Tasks:
  - Remove per-activity global handler override, or store/restore previous handler in lifecycle-safe way.
  - Ensure crash logging behavior remains intact.
- Acceptance Criteria:
  - No leaked `PlayerActivity` reference through global handler.
  - App-level crash handling remains owned by `GlobalExceptionHandler`.

### REL-003: Eliminate global-search dispatcher thread leak
- Priority: P1
- Risk: Thread accumulation and memory growth over repeated screen opens
- Problem: Search screen models allocate fixed thread pools via `newFixedThreadPool(...).asCoroutineDispatcher()` and never close.
- Files:
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/globalsearch/AnimeSearchScreenModel.kt`
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/globalsearch/MangaSearchScreenModel.kt`
- Tasks:
  - Replace custom executor dispatcher with owned/reused dispatcher strategy.
  - If a custom dispatcher remains, close it in `onDispose`.
- Acceptance Criteria:
  - No leaked thread pools after screen disposal.
  - Search throughput remains acceptable.

### REL-004: Fix stale-result race in anime global search
- Priority: P1
- Risk: Incorrect UI state from out-of-order writes
- Problem: Anime global search does not cancel prior `searchJob` before starting a new one.
- Files:
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/globalsearch/AnimeSearchScreenModel.kt`
- Tasks:
  - Cancel in-flight search job before launching a new search.
  - Add query token or guard to drop stale results if needed.
- Acceptance Criteria:
  - Rapid query/filter changes cannot show stale prior results.
  - Behavior matches manga implementation semantics.

## Phase 2: Medium-Risk Runtime Refactors (P2)

### REL-005: Remove `runBlocking` fallback from `getOrStub` source resolution
- Priority: P2
- Risk: Main-thread jank / ANR under I/O pressure
- Problem: `getOrStub` blocks with `runBlocking` when source is absent.
- Files:
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/source/anime/AndroidAnimeSourceManager.kt`
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/source/manga/AndroidMangaSourceManager.kt`
- Tasks:
  - Introduce non-blocking retrieval path (suspend/preload/cache-first).
  - Update UI-path callsites to avoid forced blocking source resolution.
- Acceptance Criteria:
  - No `runBlocking` in source manager lookup path.
  - Existing source fallback behavior preserved.

### REL-006: Fix stub source map update race/no-op
- Priority: P2
- Risk: stale source metadata and repeated fallback work
- Problem: DB subscription path builds temporary map but does not apply updates to `stubSourcesMap`.
- Files:
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/source/anime/AndroidAnimeSourceManager.kt`
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/source/manga/AndroidMangaSourceManager.kt`
- Tasks:
  - Apply synchronized/atomic map update semantics.
  - Add regression test for DB-driven stub source updates.
- Acceptance Criteria:
  - Stub map reflects repository emissions correctly.
  - No stale entries after source updates.

### REL-007: Remove blocking extension loader `runBlocking` usage
- Priority: P2
- Risk: startup slowdown / ANR pressure
- Problem: Extension loader uses `runBlocking` for concurrent load aggregation.
- Files:
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/extension/anime/util/AnimeExtensionLoader.kt`
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/extension/manga/util/MangaExtensionLoader.kt`
- Tasks:
  - Convert loading API to suspend-first path.
  - Ensure manager initialization doesn’t block main-start path.
- Acceptance Criteria:
  - No blocking extension loading on UI startup path.
  - Extension discovery behavior unchanged.

### REL-008: Remove `runBlocking` from download queue restore path
- Priority: P2
- Risk: blocking and delayed restore under heavy data
- Problem: Download stores use `runBlocking` during restore loops.
- Files:
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloadStore.kt`
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadStore.kt`
- Tasks:
  - Refactor restore APIs to suspend and batch load entities.
  - Preserve queue order and migration behavior.
- Acceptance Criteria:
  - No `runBlocking` in restore loops.
  - Queue restore remains deterministic.

### REL-009: Make Simkl user fetch suspend-safe
- Priority: P2
- Risk: latent blocking call in auth flow
- Problem: `SimklApi.getCurrentUser()` uses `runBlocking`.
- Files:
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/data/track/simkl/SimklApi.kt`
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/data/track/simkl/Simkl.kt`
- Tasks:
  - Convert to suspend API and update callers.
- Acceptance Criteria:
  - No `runBlocking` in Simkl API layer.
  - Login flow behavior unchanged.

### REL-010: Enforce explicit lifecycle for long-lived custom scopes
- Priority: P2
- Risk: unmanaged coroutine lifetime / hard-to-debug leaks
- Problem: Multiple singleton/service classes own custom `CoroutineScope` without explicit shutdown contract.
- Files (initial set):
  - `/Users/rayyacub/Documents/rayniyomi/domain/src/main/java/tachiyomi/domain/storage/service/StorageManager.kt`
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/source/anime/AndroidAnimeSourceManager.kt`
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/source/manga/AndroidMangaSourceManager.kt`
- Tasks:
  - Define ownership/cancellation contract per manager.
  - Add explicit close/dispose hooks where appropriate.
  - Align with coroutine scope policy doc.
- Acceptance Criteria:
  - Each custom scope has documented owner + cancellation path.
  - No orphaned long-lived jobs after owner shutdown.

### REL-011: Tighten reader holder coroutine cleanup
- Priority: P2
- Risk: low-probability holder-scope leaks in edge recycle/detach paths
- Problem: holder-level `MainScope()` is used; jobs are canceled but scope lifecycle can be tightened.
- Files:
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerPageHolder.kt`
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonPageHolder.kt`
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/pager/PagerTransitionHolder.kt`
  - `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/ui/reader/viewer/webtoon/WebtoonTransitionHolder.kt`
- Tasks:
  - Replace ad-hoc scope lifecycle with explicit cancel on terminal lifecycle point.
  - Verify no regressions in page loading/retry.
- Acceptance Criteria:
  - No active holder coroutines after recycle/detach.
  - Reader scrolling/loading remains stable.

## Phase 3: Hardening, Tooling, and Regression Guards

### REL-012: Add static guardrails for blocking calls on UI paths
- Priority: P2
- Risk: regression reintroducing ANR risks
- Tasks:
  - Add lint/detekt rule coverage for disallowed `runBlocking` in UI-layer modules.
  - Add documented exceptions list if needed.
- Acceptance Criteria:
  - CI flags new blocking-call regressions automatically.

### REL-013: Concurrency regression tests for global search
- Priority: P2
- Risk: stale UI/race regressions returning post-refactor
- Tasks:
  - Add tests for rapid query/filter mutation and stale job cancellation behavior.
- Acceptance Criteria:
  - Tests fail if stale results can overwrite latest query state.

### REL-014: Startup performance and ANR smoke tests
- Priority: P2
- Risk: startup regressions after migration/source changes
- Tasks:
  - Add baseline startup timings for migration-enabled and extension-heavy profiles.
  - Add ANR-sensitive smoke scenario in CI/device farm where feasible.
- Acceptance Criteria:
  - Startup regressions are detected before release.

### REL-015: Scope ownership audit checklist + docs update
- Priority: P3
- Risk: architectural drift
- Tasks:
  - Update `/Users/rayyacub/Documents/rayniyomi/docs/architecture/coroutine-scope-policy.md` with concrete owner examples from app modules.
  - Add PR checklist items for scope ownership and cancellation proof.
- Acceptance Criteria:
  - New coroutine scopes require explicit owner/cancellation in PR review.

## Suggested Execution Order
1. Phase 1 (`REL-001` to `REL-004`) as one stabilization milestone.
2. Phase 2 in two slices:
   - Slice A: `REL-005`, `REL-006`, `REL-007`
   - Slice B: `REL-008`, `REL-009`, `REL-010`, `REL-011`
3. Phase 3 after functional stabilization.

## Notes
- Phase 1 should be prioritized before broad UI refactors.
- Phase 2 items are medium risk and suitable for incremental PRs.
- Phase 3 reduces recurrence and improves long-term maintainability.
