# Ralph Loop State — R416 DB De-duplication

## Iteration: 16
## Last Updated: 2026-03-03
## Status: IN_PROGRESS

---

## Loop Rules (read every iteration)

**START each iteration:** (1) read this file to understand state, (2) run `git log --oneline -5`
**END each iteration:** (1) update this file — increment iteration, check off completed item, record errors/decisions, (2) commit with descriptive message
**SCOPE:** Complete exactly ONE checklist item per iteration. No more.
**TDD:** For any interactor or utility, write the FAILING test first, verify it fails, then implement.
**STUCK:** If the same error appears twice, document under `## Last Error` and try a different approach.
**VERIFICATION:**
- After SQL changes → `./gradlew :data:generateSqlDelightInterface`
- After domain Kotlin → `./gradlew :domain:compileDebugKotlin`
- After app Kotlin → `./gradlew :app:compileDebugKotlin`
**DONE:** When all items are checked, tests green, and build clean → output `<promise>ALL_CHECKLIST_ITEMS_DONE_TESTS_GREEN_BUILD_CLEAN</promise>`

---

## Checklist (one item per iteration)

### Domain Layer
- [x] `domain/.../util/TitleNormalizer.kt` — new utility, normalize(title): String
- [x] `domain/.../manga/model/DuplicateCandidate.kt` — DuplicateCandidate + DuplicateConfidence enum
- [x] `domain/.../anime/model/DuplicateCandidate.kt` — mirror for anime

### Database Migrations
- [x] `data/src/main/sqldelight/migrations/36.sqm` — normalized_title column on mangas + index (34/35 taken)
- [x] `data/src/main/sqldelightanime/migrations/138.sqm` — normalized_title column on animes + index (137 taken)

### SQL Schema + Queries
- [x] `data/.../sqldelight/data/mangas.sq` — add normalized_title to CREATE TABLE + getDuplicateLibraryMangaByNormalizedTitle query
- [x] `data/.../sqldelightanime/dataanime/animes.sq` — mirror for anime
- [x] `data/.../sqldelight/data/manga_sync.sq` — getMangaByTrackerId + updateProgressIfGreater + transferUniqueTrackers queries
- [x] `data/.../sqldelightanime/dataanime/anime_sync.sq` — mirror for anime
  → VERIFY: `./gradlew :data:generateSqlDelightInterface`

### Repository Layer
- [x] `domain/.../manga/repository/MangaRepository.kt` — add getDuplicateLibraryMangaByTracker, getDuplicateLibraryMangaByNormalizedTitle, mergeEntries
- [x] `domain/.../anime/repository/AnimeRepository.kt` — mirror for anime
- [x] `data/.../manga/MangaRepositoryImpl.kt` — implement new methods
- [x] `data/.../anime/AnimeRepositoryImpl.kt` — mirror for anime
  → VERIFY: `./gradlew :data:compileDebugKotlin`

### Domain Interactors
- [x] `domain/.../manga/interactor/GetDuplicateLibraryManga.kt` — add awaitAll() returning List<DuplicateCandidate>
- [x] `domain/.../anime/interactor/GetDuplicateLibraryAnime.kt` — mirror for anime
- [x] `domain/.../manga/interactor/MergeLibraryManga.kt` — NEW: atomic merge transaction
- [x] `domain/.../anime/interactor/MergeLibraryAnime.kt` — NEW: mirror for anime
- [x] `domain/.../interactor/ScanLibraryDuplicates.kt` — NEW: bulk scan returning List<DuplicatePair>
  → VERIFY: `./gradlew :domain:compileDebugKotlin`

### UI Layer
- [x] `i18n-aniyomi/.../base/strings.xml` — add all 7 new string keys
- [x] `app/.../entries/components/DuplicateEntryDialog.kt` — add onMerge callback + confidence subtitle
- [x] `app/.../ui/entries/manga/MangaScreenModel.kt` — awaitAll + merge handler + DuplicateManga dialog gains confidence field
- [x] `app/.../ui/entries/anime/AnimeScreenModel.kt` — mirror for anime
- [x] `app/.../settings/screen/SettingsLibraryScreen.kt` — add "Scan for duplicates" entry
- [x] `app/.../library/duplicate/DuplicateScanScreen.kt` — NEW: bulk scan results screen
  → VERIFY: `./gradlew :app:compileDebugKotlin`

### DI Registration
- [x] `app/.../domain/DomainModule.kt` — register MergeLibraryManga, MergeLibraryAnime, ScanLibraryDuplicates; update GetDuplicate factories to inject GetTracks
  → VERIFY: `./gradlew :app:compileDebugKotlin`

### Tests (TDD — write failing test BEFORE implementing each)
- [ ] `TitleNormalizerTest.kt` — normalize edge cases
- [ ] `GetDuplicateLibraryMangaTest.kt` — confidence tiers, empty case
- [ ] `MergeLibraryMangaTest.kt` — chapters reparented, trackers transferred, loser deleted
- [ ] `DuplicateEntryDialogTest.kt` — merge button visible, confidence subtitle
  → VERIFY: `./gradlew :app:testDebugUnitTest --tests "*Duplicate*" --tests "*Merge*" --tests "*TitleNorm*"`

### Final
- [ ] `./gradlew spotlessApply` — format all changed files
- [ ] `grep -r "= [a-z]*\.[a-z]*\.[a-z]*\." app/src/ domain/src/` — no fully qualified names
- [ ] `./gradlew assembleDebug` — full build clean
- [ ] CHANGELOG.md — add entry under ## Unreleased

---

## Decisions Made
- TitleNormalizer: uses `(?U)` inline flag for Unicode-aware `\w` in JVM regex (UNICODE_CHARACTER_CLASS is not a Kotlin RegexOption)
- TitleNormalizer strips leading articles before punctuation removal to avoid article+punctuation edge cases
- Migrations 34/137 already exist (tracker_enrichment feature from R414); using 36.sqm and 138.sqm instead

## Last Error
*(filled in as loop progresses)*

## Key File Paths (confirmed)
- Domain util: `domain/src/main/java/tachiyomi/domain/util/`
- Domain manga interactors: `domain/src/main/java/tachiyomi/domain/entries/manga/interactor/`
- Domain anime interactors: `domain/src/main/java/tachiyomi/domain/entries/anime/interactor/`
- Domain manga model: `domain/src/main/java/tachiyomi/domain/entries/manga/model/`
- Domain anime model: `domain/src/main/java/tachiyomi/domain/entries/anime/model/`
- Manga repository interface: `domain/src/main/java/tachiyomi/domain/entries/manga/repository/MangaRepository.kt`
- Anime repository interface: `domain/src/main/java/tachiyomi/domain/entries/anime/repository/AnimeRepository.kt`
- MangaRepositoryImpl: `data/src/main/java/tachiyomi/data/entries/manga/MangaRepositoryImpl.kt`
- AnimeRepositoryImpl: `data/src/main/java/tachiyomi/data/entries/anime/AnimeRepositoryImpl.kt`
- SQL manga: `data/src/main/sqldelight/data/mangas.sq`
- SQL anime: `data/src/main/sqldelightanime/dataanime/animes.sq`
- SQL manga_sync: `data/src/main/sqldelight/data/manga_sync.sq`
- SQL anime_sync: `data/src/main/sqldelightanime/dataanime/anime_sync.sq`
- Manga migrations: `data/src/main/sqldelight/migrations/`
- Anime migrations: `data/src/main/sqldelightanime/migrations/`
- DuplicateEntryDialog: `app/src/main/java/eu/kanade/presentation/entries/components/DuplicateEntryDialog.kt`
- MangaScreenModel: `app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/MangaScreenModel.kt`
- AnimeScreenModel: `app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreenModel.kt`
- SettingsLibraryScreen: `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsLibraryScreen.kt`
- DomainModule: `app/src/main/java/eu/kanade/domain/DomainModule.kt`
- i18n strings: `i18n-aniyomi/src/commonMain/moko-resources/base/strings.xml`
- GetMangaTracks: `domain/src/main/java/tachiyomi/domain/track/manga/interactor/GetMangaTracks.kt`
- GetAnimeTracks: `domain/src/main/java/tachiyomi/domain/track/anime/interactor/GetAnimeTracks.kt`

## Reference
- Full plan: `gh issue view 416 --repo ryacub/rayniyomi` (see latest comment)
- Branch: `claude/r416-db-deduplication`
- PR target: `ryacub/rayniyomi` base `main`
