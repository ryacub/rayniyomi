# Migration and source-filter null assertion triage (R1042)

This file records the disposition of the 15 production `!!` assertions in the
browse migration screens and the source-filter dialogs that R1032 put in the
baseline. R1042 kept all 15 and fixed none. Each site lists the call path that
was checked and the reason it stays.

R1042 depends on R1029 (#1171). R1029 is closed, so the baseline holds at 165
rows and the 15 target rows are stable.

## Rubric

An assertion earns a fix only when a null produces a wrong outcome, and a code
path reaches that null. A wrong outcome is wrong control flow, a stuck state, or
lost work. See Earned Changes in `AGENTS.md`.

A guard against a state that no code path in this repository can produce is not a
fix. Every one of the 15 sites is inherited from upstream without change.
The null needs a state that this repository does not build, or a race with no
demonstrated impact.

## Evidence: Crashlytics

The Crashlytics top-issue report over the 90-day window (2026-06-14 to
2026-09-11, top 40 issues) has no event for any of the 15 sites. There is no
`SourceFilterAnimeDialog`, `SourceFilterMangaDialog`, `MigrateAnimeScreen`,
`MigrateMangaScreen`, `MigrateAnimeSearchScreen`, or `MigrateMangaSearchScreen`
crash, and no migration `getAnime`/`getManga` null pointer. The one browse crash
in the window is `BrowseMangaSourceToolbar`, which is a different site and out of
this package.

## Kept (15)

### Migration screen source title (2)

- `MigrateAnimeScreen.kt` `title = state.source!!.name`
- `MigrateMangaScreen.kt` `title = state.source!!.name`

Non-null by construction. `MigrateAnimeScreenModel` sets `source` with
`sourceManager.getOrStub(sourceId)`. `getOrStub` returns a non-null
`AnimeSource`, and it returns a stub source when the key is unknown. The screen
reads `state.source!!` only after `if (state.isLoading)` returns the loading UI,
and `isLoading` is `source == null || titleList == null`. So `source` is not null
at the read. The manga screen matches the anime screen. Kept.

### Migration screen model anime and manga lookup (4)

- `AnimeMigrateSearchScreenDialogScreenModel.kt` `val anime = getAnime.await(animeId)!!`
- `MigrateAnimeSearchScreenModel.kt` `val anime = getAnime.await(animeId)!!`
- `MangaMigrateSearchScreenDialogScreenModel.kt` `val manga = getManga.await(mangaId)!!`
- `MigrateMangaSearchScreenModel.kt` `val manga = getManga.await(mangaId)!!`

Reachable only on a race, with no demonstrated impact. The screen receives
`animeId` or `mangaId` from an entry that the user opened to migrate, so the row
exists when the screen starts. `getAnime.await` returns null only if the row is
deleted between navigation and the `init` coroutine. The read runs inside
`screenModelScope.launch`, so a null throws inside a cancellable coroutine, not
on a render path. Crashlytics has no such event. Kept.

### Migration search screen selected entry (5)

- `MigrateAnimeSearchScreen.kt` `AnimeSourceSearchScreen(dialogState.anime!!, it.id, state.searchQuery)`
- `MigrateAnimeSearchScreen.kt` `oldAnime = dialogState.anime!!`
- `MigrateAnimeSearchScreen.kt` `MigrateSeasonSelectScreen(dialogState.anime!!, dialog.anime)`
- `MigrateMangaSearchScreen.kt` `MangaSourceSearchScreen(dialogState.manga!!, it.id, state.searchQuery)`
- `MigrateMangaSearchScreen.kt` `oldManga = dialogState.manga!!`

Non-null by construction for the dialog reads. The dialog model loads
`dialogState.anime` in `init`, and the `Dialog.Migrate` branch runs only after
the user opens a loaded entry. The same screen reads `dialogState.anime?.source`
with a safe call one line above the search-result read, so a null before load
produces no wrong outcome on the safe path. The search-result read needs a tap on
a result before the local database lookup returns; that lookup is a fast local
read, and Crashlytics has no event. Kept.

### Source-filter sort selection (4)

- `SourceFilterAnimeDialog.kt` `!filter.state!!.ascending`
- `SourceFilterAnimeDialog.kt` `filter.state!!.ascending`
- `SourceFilterMangaDialog.kt` `!filter.state!!.ascending`
- `SourceFilterMangaDialog.kt` `filter.state!!.ascending`

Reachable only from a state that this repository does not build.
`AnimeFilter.Sort` and `Filter.Sort` declare `state: Selection? = null`. In the
`SortItem` click handler, the `else` branch reads `filter.state!!.ascending` when
`index == filter.state?.index` is false, which includes the case where
`filter.state` is null. So a `Sort` filter that ships with a null default
selection throws on the first tap.

No source in this repository builds such a filter. The only
`Sort.Selection` construction in the tree is the UI itself, and the bundled
local source declares no `Sort` filter. A null-state `Sort` can come only from a
third-party extension that declares `Filter.Sort(name, values)` and omits the
optional `state`. The extension catalogue in this fork serves placeholder
packages, so this input has no live producer. The block is identical in the
upstream project, and Crashlytics has no event for either dialog.

The safe `filter.state?.index` call two lines above the `!!` shows the mismatch:
the same handler reads `state` safely for display and unsafely for the tap. A
guard would fix the mismatch, but the input that triggers it has no code path in
this repository and no crash history, so a fix would diverge from upstream
against an undemonstrated condition. Recorded as an observation, not a fix. Kept.

## Baseline

No assertion is removed, so no baseline row is removed. The
`scripts/null_assertion_baseline.txt` file is unchanged, and the
`checkNullAssertions` gate passes with no new or stale rows.
