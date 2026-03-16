# Implementation Plan: Migrate download queue to Compose + remove FlexibleAdapter (R528)

## Overview

Currently, the download queue screens (anime and manga) are implemented using FlexibleAdapter (a 3rd-party RecyclerView-based hierarchical adapter) with manual View binding. Both screens render expandable lists of downloads grouped by source, with drag-reorder, priority badges, progress indicators, and context menus.

This migration replaces:
1. **AnimeDownloadAdapter** + **AnimeDownloadHeaderItem/HeaderHolder** + **AnimeDownloadItem/Holder** → Compose LazyColumn with expandable state
2. **MangaDownloadAdapter** + **MangaDownloadHeaderItem/HeaderHolder** + **MangaDownloadItem/Holder** → Same

Final outcome: Pure Compose UI, zero FlexibleAdapter dependency.

## Stack
- Language: Kotlin / Android
- Test framework: JUnit4 + MockK
- Test command: `./gradlew :app:testDebugUnitTest --tests "*Download*"`
- UI framework: Jetpack Compose (Material Design 3)
- Drag-reorder library: `sh.calvin.reorderable:reorderable` (already in deps)

## Architecture
- **ScreenModel:** `AnimeDownloadQueueScreenModel` / `MangaDownloadQueueScreenModel` (owned scope via Voyager)
- **State:** `StateFlow<List<*HeaderItem>>` — data already hoisted correctly
- **UI:** Pure Composables with @Preview, state hoisting respected
- **Side effects:** Flow collection via `.collectAsStateWithLifecycle()` in Tab layer (already correct)

## Features (implement in order)

### 1. Refactor screen models to emit Compose-compatible state

**Changes:**
- Replace `AnimeDownloadHeaderItem` (FlexibleAdapter) with immutable data class annotated `@Stable`
- Replace `AnimeDownloadItem` (FlexibleAdapter) with immutable data class annotated `@Stable`
- Add `isExpanded: Boolean = true` field to header data class
- Refactor init block to emit new structure instead of FlexibleAdapter items
- Keep listener interface unchanged (still handles menu/drag)
- New data classes go in a new file `AnimeDownloadUiState.kt` (avoids circular imports with ScreenModel)

**`@Stable` requirement:** Annotate both data classes with `@androidx.compose.runtime.Stable` so Compose skips recomposition when reference equality holds. This directly pairs with R454.

**Progress performance:** In ScreenModel, use `distinctUntilChanged()` on the queue flow. Progress fields in `AnimeDownloadItem` should be `Float` (0f–1f) so the composable can use `derivedStateOf { item.progress }` to gate recomposition.

Test file: **N/A** — state transformation is UI concern per CLAUDE.md
Success: ScreenModel emits Compose-compatible data
Independent: No (blocks 2, 3)

### 2. Implement AnimeDownloadQueueScreen pure Compose

**Current:** AndroidView wrapping RecyclerView + FlexibleAdapter
**New:** LazyColumn with:
- Reorderable headers (drag to reorder)
- Expandable headers (click to toggle visibility via `AnimatedVisibility`)
- Download items with progress bars, badges, priority indicators
- Popup menus on items (move to top/bottom, cancel single/series)
- Empty state when queue is empty

Layout preserved:
- Header: "[Source Name] (count)" with drag handle
- Item: Title, progress bar, priority badge, menu button

**Reorderable library API (exact pattern):**
```kotlin
val reorderState = rememberReorderableLazyListState(
    lazyListState = lazyListState,
    onMove = { from, to ->
        // Only allow reorder within same source group
        // from.index and to.index are indices in the flattened list
        // Map back to header indices; reject if headers differ
        val fromHeader = findHeaderIndex(items, from.index)
        val toHeader = findHeaderIndex(items, to.index)
        if (fromHeader == toHeader) {
            screenModel.reorderDownload(from.index, to.index)
        }
    },
)
LazyColumn(state = lazyListState) {
    items(items, key = { it.uniqueKey }) { item ->
        ReorderableItem(reorderState, key = item.uniqueKey) { isDragging ->
            // Modifier order matters: reorderable THEN detectReorderAfterLongPress
            Row(Modifier.detectReorderAfterLongPress(reorderState)) { ... }
        }
    }
}
```
Only headers are draggable (long-press on drag handle icon). Items within a header are NOT draggable.

**Cross-source move prevention (concrete):** In `onMove`, compute `headerIndexOf(flatIndex)` by scanning the flat list for the nearest preceding header entry. If `headerIndexOf(from)` != `headerIndexOf(to)`, return without calling screenModel — the library will snap back automatically.

**Expand/collapse:** Use `AnimatedVisibility(visible = header.isExpanded)` wrapping the items block. On drag start, call `screenModel.collapseAll()`; on drag end, call `screenModel.expandHeader(draggedHeaderId)`. This replicates FlexibleAdapter's `collapseAll`/`expandGroup` behavior.

**Popup menu:** Use `Box { IconButton(onClick = { expanded = true }) { ... }; DropdownMenu(expanded, onDismissRequest = { expanded = false }) { ... } }` — `DropdownMenu` in Compose anchors to the `Box` bounds automatically.

**Progress:** `val progress by remember { derivedStateOf { item.progress } }` inside each item composable to prevent host recomposition on every tick.

Test file: **N/A** — UI-only per CLAUDE.md
Success: Anime download screen renders visually identical, expand/collapse works, drag-drop reorders
Independent: No (depends on Feature 1)

### 3. Implement MangaDownloadQueueScreen pure Compose

**Current:** AndroidView wrapping RecyclerView + FlexibleAdapter (identical to anime)
**New:** Same LazyColumn pattern as anime but with `MangaDownload` data — reuse all UI components from Feature 2 where possible (extract shared `DownloadQueueItem`, `DownloadQueueHeader` composables).

Differences from anime:
- `MangaDownload` instead of `AnimeDownload`
- Progress calc: `totalProgress = pages.sumOf(progress)`, `downloadedImages = count(READY)`
- Data classes in `MangaDownloadUiState.kt` (same `@Stable` + `distinctUntilChanged` pattern)

Test file: **N/A** — UI-only
Success: Manga download screen renders, expand/collapse works, drag-drop reorders
Independent: No (depends on Feature 1)

### 4. Remove FlexibleAdapter dependency

**Files to delete (10 files):**
- `app/src/main/java/eu/kanade/tachiyomi/ui/download/anime/AnimeDownloadAdapter.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/download/anime/AnimeDownloadHeaderItem.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/download/anime/AnimeDownloadHeaderHolder.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/download/anime/AnimeDownloadItem.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/download/anime/AnimeDownloadHolder.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/download/manga/MangaDownloadAdapter.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/download/manga/MangaDownloadHeaderItem.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/download/manga/MangaDownloadHeaderHolder.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/download/manga/MangaDownloadItem.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/download/manga/MangaDownloadHolder.kt`

**Build file changes:**
- Remove: `implementation(libs.flexible.adapter.core)` from `app/build.gradle.kts`
- Remove: `flexible-adapter-core = ...` from `gradle/libs.versions.toml`

Test file: **N/A** — verified by compile
Success: No FlexibleAdapter refs in codebase, compileDebugKotlin succeeds
Independent: No (blocked by 2, 3)

## Current behavior to preserve

### Data Flow
- ScreenModel watches `downloadManager.queueState` → emits grouped-by-source list
- Flow updates automatically on downloads added/removed/reordered
- Progress updates via manager flows

### UI Interactions
1. **Expand/Collapse:** Click header → toggle items visibility
2. **Drag-Reorder:** Drag header (not items) → reorder, collapse on start, expand on end
3. **Item Menu:** Options for move/cancel (single and series-wide)
4. **Progress Indication:** Bar, text, badges, drag-state visual

## Compose UI patterns

- Pure data classes (no mutable FlexibleAdapter items)
- LazyColumn with reorderable library
- State hoisting via StateFlow in ScreenModel
- @Preview on all Composables
- No AndroidView, no LayoutInflater
- Material Design 3 only

## Completion Criteria

- `grep -r "FlexibleAdapter\|flexibleadapter" app/src` returns zero results
- `./gradlew :app:compileDebugKotlin` succeeds
- Screens render identically to current
- Expand/collapse, drag-reorder, menus functional
- Progress bars, badges, empty state working
- CI passes

## Edge Cases

### Feature 1: State Transformation
- Empty queue: Emit empty list → empty state screen
- Downloaded items: Stay in queue until user cancels
- Error states: Show reason text (preserve displayReasonText)
- Rapid updates: Use distinctUntilChanged() in ScreenModel

### Feature 2-3: Compose UI
- Reorder validation: Prevent cross-source moves
- Collapse headers on drag start, expand on drag end
- Menu item visibility: Hide move_to_top if already at 0, hide move_to_bottom if at end
- Accessibility: contentDescription, POLITE region

### Feature 4: Cleanup
- Delete ScreenModel.adapter field (no longer needed)
- Delete ScreenModel.controllerBinding field (Compose manages)

## Risks

1. **Reorderable library:** Already in deps (2.4.3), must validate cross-source move prevention

2. **FlexibleAdapter behavior:** 
   - collapseAll/expandAll on drag → replicate with StateFlow updates
   - Items not independently draggable → configure Reorderable correctly
   - shouldMove() validation → replicate in onMove callback

3. **Listener interface:** Don't delete—menu click logic still valid, just no longer called from ViewHolder

4. **Progress updates:**
   - Anime: 0-100% via video object → store as `Float` (0f–1f) in `AnimeDownloadItem`
   - Manga: Sum of pages progress → `Float` normalized
   - Both update via manager flows + holder notifications
   - In Compose: `collectAsStateWithLifecycle()` drives recomposition, but gate per-item progress via `derivedStateOf` to prevent full-list recomposition on every tick
   - Use `distinctUntilChanged()` on ScreenModel flow to suppress no-op emissions

5. **Testing:** UI-only per CLAUDE.md—no unit tests required for Compose, ScreenModel logic untested

## Implementation Order

1. Create immutable data classes replacing FlexibleAdapter items
2. Refactor ScreenModels to emit new state
3. Implement AnimeDownloadQueueScreen Composable, replace AndroidView
4. Implement MangaDownloadQueueScreen Composable, replace AndroidView
5. Delete 10 FlexibleAdapter files
6. Remove from build files
7. Compile, test, verify
