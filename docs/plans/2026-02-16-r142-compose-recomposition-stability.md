# R142: Stabilize Compose Recomposition in Library Composables

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Eliminate unnecessary recompositions in library grid/list composables by replacing O(n) selection checks with O(1) Set lookups and memoizing conditional lambdas.

**Architecture:** Two mechanical changes across 6 symmetric files. No business logic touched. The `selection` list (from ViewModel state) is converted to a `HashSet<Long>` once per composable recomposition via `derivedStateOf`, so per-item `isSelected` lookups become O(1). Conditional `onClickContinueViewing` lambdas are wrapped with `remember` inside each item block to avoid creating new lambda instances on every recomposition.

**Tech Stack:** Jetpack Compose, `remember`, `derivedStateOf` (both from `androidx.compose.runtime`)

**Ticket:** R142 #230 — T1, no TDD required (UI-only optimisation, no business logic)

---

## Background: Why This Matters

In a library with 500+ items, scrolling triggers recompositions of every visible item. Each item currently calls:
```kotlin
isSelected = selection.fastAny { it.id == libraryItem.libraryManga.id }
```
`fastAny` iterates the **entire selection list** for each item. With 10 selected items and 30 visible items that's 300 iterations per frame. The fix computes a `HashSet` once per `selection` change, making each item lookup O(1).

The `onClickContinueViewing` conditional creates a new lambda instance inline on every recomposition even when the inputs haven't changed, preventing Compose from skipping the item.

---

## Task 1: Create Worktree

**Step 1: Create isolated workspace**
```bash
cd /Users/rayyacub/Documents/rayniyomi
git worktree add .worktrees/claude/r142-compose-stability -b claude/r142-compose-stability ryacub/main
```

**Step 2: Verify clean state**
```bash
cd .worktrees/claude/r142-compose-stability
git log --oneline -3
```
Expected: Shows recent main commits, no extra history.

---

## Task 2: Fix Manga Compact Grid

**File:** `app/src/main/java/eu/kanade/presentation/library/manga/MangaLibraryCompactGrid.kt`

**Step 1: Add imports**

Add to the import block:
```kotlin
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
```

**Step 2: Add `selectedIds` derivation**

Inside `MangaLibraryCompactGrid`, after the function signature and before the `LazyLibraryGrid` call, add:
```kotlin
val selectedIds by remember { derivedStateOf { selection.mapTo(HashSet()) { it.id } } }
```

**Step 3: Replace `fastAny` with Set lookup**

Change:
```kotlin
isSelected = selection.fastAny { it.id == libraryItem.libraryManga.id },
```
To:
```kotlin
isSelected = libraryItem.libraryManga.id in selectedIds,
```

**Step 4: Memoize `onClickContinueViewing` lambda**

Change:
```kotlin
onClickContinueViewing = if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
    { onClickContinueReading(libraryItem.libraryManga) }
} else {
    null
},
```
To:
```kotlin
onClickContinueViewing = remember(onClickContinueReading, libraryItem.libraryManga.id, libraryItem.unreadCount) {
    if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
        { onClickContinueReading(libraryItem.libraryManga) }
    } else {
        null
    }
},
```

**Step 5: Remove `fastAny` import** (if no longer used in this file)

Remove: `import androidx.compose.ui.util.fastAny`

**Final file should look like:**
```kotlin
package eu.kanade.presentation.library.manga

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import eu.kanade.presentation.library.components.DownloadsBadge
import eu.kanade.presentation.library.components.EntryCompactGridItem
import eu.kanade.presentation.library.components.LanguageBadge
import eu.kanade.presentation.library.components.LazyLibraryGrid
import eu.kanade.presentation.library.components.UnviewedBadge
import eu.kanade.presentation.library.components.globalSearchItem
import eu.kanade.tachiyomi.ui.library.manga.MangaLibraryItem
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.library.manga.LibraryManga

@Composable
internal fun MangaLibraryCompactGrid(
    items: List<MangaLibraryItem>,
    showTitle: Boolean,
    columns: Int,
    contentPadding: PaddingValues,
    selection: List<LibraryManga>,
    onClick: (LibraryManga) -> Unit,
    onLongClick: (LibraryManga) -> Unit,
    onClickContinueReading: ((LibraryManga) -> Unit)?,
    searchQuery: String?,
    onGlobalSearchClicked: () -> Unit,
) {
    val selectedIds by remember { derivedStateOf { selection.mapTo(HashSet()) { it.id } } }
    LazyLibraryGrid(
        modifier = Modifier.fillMaxSize(),
        columns = columns,
        contentPadding = contentPadding,
    ) {
        globalSearchItem(searchQuery, onGlobalSearchClicked)

        items(
            items = items,
            contentType = { "manga_library_compact_grid_item" },
        ) { libraryItem ->
            val manga = libraryItem.libraryManga.manga
            EntryCompactGridItem(
                isSelected = libraryItem.libraryManga.id in selectedIds,
                title = manga.title.takeIf { showTitle },
                coverData = MangaCover(
                    mangaId = manga.id,
                    sourceId = manga.source,
                    isMangaFavorite = manga.favorite,
                    url = manga.thumbnailUrl,
                    lastModified = manga.coverLastModified,
                ),
                coverBadgeStart = {
                    DownloadsBadge(count = libraryItem.downloadCount)
                    UnviewedBadge(count = libraryItem.unreadCount)
                },
                coverBadgeEnd = {
                    LanguageBadge(
                        isLocal = libraryItem.isLocal,
                        sourceLanguage = libraryItem.sourceLanguage,
                    )
                },
                onLongClick = { onLongClick(libraryItem.libraryManga) },
                onClick = { onClick(libraryItem.libraryManga) },
                onClickContinueViewing = remember(onClickContinueReading, libraryItem.libraryManga.id, libraryItem.unreadCount) {
                    if (onClickContinueReading != null && libraryItem.unreadCount > 0) {
                        { onClickContinueReading(libraryItem.libraryManga) }
                    } else {
                        null
                    }
                },
            )
        }
    }
}
```

---

## Task 3: Fix Manga Comfortable Grid

**File:** `app/src/main/java/eu/kanade/presentation/library/manga/MangaLibraryComfortableGrid.kt`

Apply the exact same pattern as Task 2:
- Add `remember`/`derivedStateOf`/`getValue` imports, remove `fastAny` import
- Add `val selectedIds by remember { derivedStateOf { selection.mapTo(HashSet()) { it.id } } }` before `LazyLibraryGrid`
- Replace `selection.fastAny { it.id == libraryItem.libraryManga.id }` → `libraryItem.libraryManga.id in selectedIds`
- Wrap `onClickContinueViewing` with `remember(onClickContinueReading, libraryItem.libraryManga.id, libraryItem.unreadCount) { ... }`

Note: This file uses `libraryItem.unreadCount` (not `unseenCount`) — verify correct field name when editing.

---

## Task 4: Fix Manga List

**File:** `app/src/main/java/eu/kanade/presentation/library/manga/MangaLibraryList.kt`

Same pattern:
- Add `remember`/`derivedStateOf`/`getValue` imports, remove `fastAny` import
- Add `selectedIds` before `FastScrollLazyColumn`
- Replace `fastAny` with `in selectedIds`
- Wrap `onClickContinueViewing` with `remember(onClickContinueReading, libraryItem.libraryManga.id, libraryItem.unreadCount) { ... }`

---

## Task 5: Fix Anime Compact Grid

**File:** `app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryCompactGrid.kt`

Same pattern, but with anime field names:
- `libraryItem.libraryAnime.id` (not `libraryManga.id`)
- `libraryItem.unseenCount` (not `unreadCount`)
- `onClickContinueWatching` (not `onClickContinueReading`)
- `selection: List<LibraryAnime>`

```kotlin
val selectedIds by remember { derivedStateOf { selection.mapTo(HashSet()) { it.id } } }
```
Inside items:
```kotlin
isSelected = libraryItem.libraryAnime.id in selectedIds,
onClickContinueViewing = remember(onClickContinueWatching, libraryItem.libraryAnime.id, libraryItem.unseenCount) {
    if (onClickContinueWatching != null && libraryItem.unseenCount > 0) {
        { onClickContinueWatching(libraryItem.libraryAnime) }
    } else {
        null
    }
},
```

---

## Task 6: Fix Anime Comfortable Grid

**File:** `app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryComfortableGrid.kt`

Same as Task 5 (anime field names, `unseenCount`, `onClickContinueWatching`).

---

## Task 7: Fix Anime List

**File:** `app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryList.kt`

Same as Task 5 (anime field names, `unseenCount`, `onClickContinueWatching`).

---

## Task 8: Build Verification

**Step 1: Run spotless**
```bash
cd .worktrees/claude/r142-compose-stability
./gradlew :app:spotlessApply
```
Expected: BUILD SUCCESSFUL. If any files are reformatted, verify the changes are only whitespace/formatting.

**Step 2: Compile**
```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL. No compilation errors.

**Step 3: Check for leftover `fastAny` in changed files**
```bash
grep -n "fastAny" \
  app/src/main/java/eu/kanade/presentation/library/manga/MangaLibraryCompactGrid.kt \
  app/src/main/java/eu/kanade/presentation/library/manga/MangaLibraryComfortableGrid.kt \
  app/src/main/java/eu/kanade/presentation/library/manga/MangaLibraryList.kt \
  app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryCompactGrid.kt \
  app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryComfortableGrid.kt \
  app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryList.kt
```
Expected: No output (all `fastAny` usages removed from these files).

---

## Task 9: Commit

```bash
cd .worktrees/claude/r142-compose-stability
git add \
  app/src/main/java/eu/kanade/presentation/library/manga/MangaLibraryCompactGrid.kt \
  app/src/main/java/eu/kanade/presentation/library/manga/MangaLibraryComfortableGrid.kt \
  app/src/main/java/eu/kanade/presentation/library/manga/MangaLibraryList.kt \
  app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryCompactGrid.kt \
  app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryComfortableGrid.kt \
  app/src/main/java/eu/kanade/presentation/library/anime/AnimeLibraryList.kt

git commit -m "perf: stabilize library recomposition with derivedStateOf and remembered lambdas (R142)"
```

---

## Task 10: Push PR

```bash
git push ryacub claude/r142-compose-stability

gh pr create \
  --title "perf: stabilize library recomposition with derivedStateOf and remembered lambdas (R142)" \
  --body "$(cat <<'EOF'
## Objective
Eliminate unnecessary Compose recompositions in library grid/list composables by replacing O(n) selection checks and unstable lambda captures with O(1) Set lookups and memoized lambdas.

## Scope
- `MangaLibraryCompactGrid.kt` — `derivedStateOf` selection + remembered lambda
- `MangaLibraryComfortableGrid.kt` — same
- `MangaLibraryList.kt` — same
- `AnimeLibraryCompactGrid.kt` — same
- `AnimeLibraryComfortableGrid.kt` — same
- `AnimeLibraryList.kt` — same

No visual changes. No architecture changes. No new composables.

## Verification
- `./gradlew :app:spotlessApply` — passes
- `./gradlew :app:assembleDebug` — passes
- No `fastAny` remaining in the 6 changed files

## Risk
Low (T1). Pure performance optimisation. No logic changes, no new APIs. Selection behaviour is identical — `derivedStateOf` produces the same Boolean per item as `fastAny`, just computed once per selection change instead of once per item per recomposition.

Closes #230
EOF
)"
```
