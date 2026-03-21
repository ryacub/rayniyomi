# Changelog

All notable changes to this project will be documented in this file.

The format is a modified version of [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
- `Added` - for new features.
- `Changed ` - for changes in existing functionality.
- `Improved` - for enhancement or optimization in existing functionality.
- `Removed` - for now removed features.
- `Fixed` - for any bug fixes.
- `Other` - for technical stuff.

## Unreleased

### Added

- **Custom accent color theme** — Material 3 app-wide theming from a user-selected accent seed; generates light/dark color schemes with Android 14 contrast-awareness and readability guardrails (contrast clamp + fallback)
- **Download crash notification** — notifies the user when the anime or manga download job crashes repeatedly (threshold: 3 consecutive crashes), with a tap-to-open link to the download manager
- **Custom app theme accent controls** — custom app theme is now selectable in Appearance settings with curated accent swatches and one-tap reset to default palette
- **Advanced custom accent workflow** — Appearance settings now includes an advanced color editor route and recent custom accent recall (last 5 applied colors)
- **Theme instrumentation coverage** — added Android instrumentation tests for custom accent persistence, reset behavior, and unset-seed fallback to default `TachiyomiColorScheme`
- **LightNovelPluginManager unit tests** — 37 tests covering install flow, manifest validation, update policy, APK download/checksum verification, install launch, in-flight mutex deduplication, error recovery, and orphaned APK cleanup
- **Persist dialog/form state across rotation** — PIN setup, PIN change (step/value/error), and enrichment chooser source selection now survive configuration changes via `rememberSaveable`
- **PIN error feedback** — shows an error message when saving a new PIN fails (e.g., storage write error), instead of silently closing the dialog
- **Download queue** — migrated anime and manga download queue screens from RecyclerView/FlexibleAdapter to Jetpack Compose; supports drag-to-reorder sources, per-item progress display, and move-to-top/bottom actions; removes flexibleadapter dependency
- **Battery optimization prompt** — shows a one-time dialog when queuing 10 or more downloads while the app is subject to battery optimization; offers direct navigation to system settings to exempt the app

### Fixed

- Browse tab reselect now opens anime or manga global search based on the current Browse context instead of always defaulting to anime search
- Coroutine cancellation no longer surfaces as a user-visible error in Discover and entry enrichment screens
- One-off UI events in Migrate and PlayerSettingsCustomButton screens no longer drop on delivery when the UI collector is temporarily inactive during lifecycle transitions; channels switched to buffered
- Migrate rayniyomi-specific screen state collection to `collectAsStateWithLifecycle()` — stops background Flow collection when UI is STOPPED
- Preserve Crashlytics build ID in release artifacts via release resource pipeline and shrinker keep rule
- Keep RoomDatabase subclass constructors in release builds to prevent WorkManager startup crash from R8 stripping the no-arg constructor
- MPV subtitle font sync now runs deterministically in initializer IO flow with idempotent copy/cleanup behavior and failure-safe per-file handling
- Guard source ID generation against invalid extension metadata to prevent `AnimeHttpSource.getId()`/`HttpSource.getId()` null-crash paths

### Changed

- All scoped Compose screen-level state collection migrated from `collectAsState()` to `collectAsStateWithLifecycle()` across `app/` screens and tabs; lifecycle-aware `Preference<T>.collectAsStateWithLifecycle()` bridge added in `presentation-core`
- Remove redundant `collectAsState()` calls in `AnimeUpdatesTab` and `MangaUpdatesTab` actions bars — reuse already-collected `state` instead of re-subscribing to the same flow

### CI

- Add `workflow_dispatch` trigger to build workflow for manual release runs when automatic tag-triggered CI is blocked by workflow-file security restrictions
- Replace `[skip ci]` auto-bump strategy with actor-based job condition to unblock tag-triggered release builds
- Add path-scoped PR theme instrumentation workflow running `:app:connectedDebugAndroidTest` on emulator with test-report/logcat artifact upload on failure

### Other

- Unit tests for `PlayerFileLoadedHandler` (29 tests) and `PlayerMpvInitializer` (16 tests); adds `MPVLibProxy` abstraction to both classes to enable JNI-safe unit testing
- Documented theming migration and fallback behavior (custom accent unset fallback, custom mode night-mode mapping, enum fallback safety, and lowercase legacy migration normalization) in README and inline code comments
- Compose stack upgrade: BOM → 2026.03.00, `activity-compose` → 1.13.0
- AndroidX step-up: `core-ktx` → 1.18.0, Lifecycle → 2.10.0, Paging → 3.4.2, WorkManager → 2.11.1, media/mediarouter bumps
- Core runtime dependency upgrade: jsoup → 1.22.1, Coil → 3.4.0, Material → 1.13.0, OkIO → 3.17.0
- Firebase BOM → 34.10.0; migrate analytics and crashlytics from deprecated `-ktx` modules to base modules
- Test/tooling upgrade: JUnit Jupiter → 6.0.3, Kotest → 6.1.7, MockK → 1.14.9, unifile snapshot update
- Align Firebase config comments in `build.gradle.kts` with actual runtime configuration
- Custom theme mode foundation: added `ThemeMode.CUSTOM` and `AppTheme.CUSTOM` enum values wired with safe fallback to system behavior; no UI exposure yet
- Pin real SHA-256 certificate fingerprint (`f3565300…`) for LightNovel plugin trust verification; removes placeholder fingerprints
- Remove unused `CoroutineScope` parameter from `PlayerMpvInitializer` constructor
- Remove dead `rollbackToLastGood()` stub and `ROLLBACK_NOT_AVAILABLE` error code from `LightNovelPluginManager`; converted 4 deferred TODO comments to tracked GitHub issues (#536–#539)
- Backfilled missing `Unreleased` entries from previously merged Codex PRs, deduped against existing changelog items

## [0.18.1.75] - 2026-03-13

### Fixed

- Resolved startup crash on release builds: Crashlytics build ID was not being embedded because the Gradle plugin was applied conditionally via a task-name check that is unreliable under Gradle configuration cache. Plugin is now applied unconditionally; Firebase dependencies are scoped to release builds only so debug builds remain unaffected.

## [0.18.1.74] - 2026-03-13

### Fixed

- Resolved startup crash on release builds caused by missing Crashlytics Gradle plugin

## [0.18.1.73] - 2026-03-13

### Security

- **Encrypted credential storage** — PIN hash, PIN salt, and tracker API tokens (AniList, MAL, Kitsu, Shikimori, Bangumi, Simkl) are now stored using Android Keystore AES-256-GCM encryption instead of plaintext SharedPreferences; existing credentials are automatically migrated on first launch

## [0.18.1.66] - 2026-03-11

### Added

- **Library de-duplication** — intelligent duplicate detection on add-to-library using tracker IDs (MAL/AniList) and normalized title matching; merge two library entries while preserving read progress, categories, history, and tracker data; bulk de-duplication scan available in Library settings
- **Cast UI components** — mini-controller overlay with drag-to-seek progress bar, subtitle track picker with ASS/SSA format filtering, subtitle style settings (font size, color, edge type), and Cast button integrated into player controls
- **Long-press speed boost gesture** — hold during playback to temporarily increase speed, release to resume normal playback
- **Download status transparency** — real-time anime download progress with stall detection and automatic recovery, manga download progress with low-storage detection and guided recovery
- **Dynamic entry cover theming** ([#413](https://github.com/ryacub/rayniyomi/issues/413)) — optional cover-based accent colors for manga/anime entry screens and light novel main/reader surfaces with contrast-checked accessibility fallbacks
- **Light novel transfer status tracking** — explicit status contract for light novel imports with accessibility-hardened progress announcements
- **Source health tracking** — manga sources now show health status badges (green check, yellow warning, red error) indicating whether they return titles successfully. Broken sources are hidden by default with a preference toggle to show them. Pull-to-refresh re-checks all sources with a summary snackbar
- **Anime source health tracking** — anime sources now show health status badges indicating whether they return titles successfully. Broken sources are hidden by default with a preference toggle to show them. Pull-to-refresh re-checks all sources with a summary snackbar
- **Discover feed (phase 1)** — added a new Discover screen under More that aggregates tracker-based recommendations across your library with ranked reasons (multi-tracker, recent activity, high-score seeds, and genre affinity)
- **Tracker enrichment cache** — tracker metadata (ratings, genres, tags, related titles) aggregated across all logged-in services and cached locally per entry; manga and anime entry detail screens now surface a "More like this" recommendation row seeded by tracker data with a manual refresh action
- **Bidirectional tracker sync** — reading and watch progress now syncs back to MAL, AniList, Kitsu, and other linked trackers automatically on a configurable schedule and on app foreground; manual "Sync now" entry in Tracking settings; remote deletions unlink local tracker entries while preserving local read/seen state
- **Light novel sources browse tab** — dedicated Browse tab listing all installed light novel plugins with a call-to-action for first install; tab appears reactively when the plugin becomes available and collapses cleanly on uninstall

### Improved

- Enabled Gradle configuration cache for faster incremental builds by removing unmaintained shortcut-helper plugin
- Documented all Gradle build settings with rationale comments
- Unified download status tracking across anime and manga modules with shared status APIs
- Throttled light novel import progress updates to reduce UI jank with improved accessibility announcements
- Coroutine failures in extension managers caught and logged instead of silently crashing
- Improved recommendation accessibility in Discover and entry recommendation surfaces with clearer semantic labels, refresh announcements, and non-color-only status cues
- **BulkEnrichmentCoordinator parallel execution** — manga and anime enrichment jobs now await together in a single `awaitAll()` call instead of two sequential `forEach { it.await() }` groups; the first chronological failure propagates immediately without waiting for the remaining group to finish
- **EntryEnrichmentCoordinator deduplication** — extracted shared generic `refreshEntry` function to eliminate ~110 lines of duplicated manga/anime enrichment logic; `refreshManga` and `refreshAnime` now delegate via lambda-injected dependencies with identical behavior
- **DiscoverFeedCoordinator throughput** — `buildFeed` is 33% faster via single-pass library genre normalization, lazy sequence pipeline for recommendation deduplication, and direct HashSet accumulation replacing intermediate collection chains
- Migrated enrichment coordinator coroutine tests from `runBlocking` to `runTest` for proper virtual-time execution and consistency with project testing standards

### Fixed

- Applied FLAG_SECURE in UnlockActivity.onCreate() synchronously before setContent{}, closing the brief window where the PIN entry screen was visible in the task switcher and accessible to screen capture tools
- Volume gesture sensitivity normalized to device-independent float space matching brightness, ensuring consistent volume changes across devices with different audio step counts
- Fixed clean-build failure caused by locales config task running at configuration time instead of execution time
- App icon now renders with correct purple background instead of white
- Notification icons use vector resources instead of decoded bitmaps to prevent memory leaks
- Backup output streams properly closed and force-unwrap crashes eliminated in backup, download, and installer flows
- Anime download manager coroutine scope properly cancelled on shutdown to prevent leaked jobs
- Tracker source lists now stored as JSON instead of comma-delimited strings, preventing data corruption when tracker names contain ", "; legacy entries are migrated transparently on first read
- Anime downloader restore failures now logged and notification errors isolated
- Migration chain no longer silently passes when individual migrations fail
- Error-log notification URI creation guarded against file write failures
- Tracker HTTP 429 retry moved to non-blocking API layer instead of sleeping on the caller thread
- Enrichment refresh now times out gracefully instead of hanging indefinitely; recommendation scoring constants documented and capped to preserve low-confidence bucket ordering
- Error-log file write failures now return explicit sealed result types instead of silently failing
- Migrated PlayerActivity onBackPressed to OnBackPressedCallback for Android 16 compatibility
- Version name restored to correct upstream-tracking scheme
- Reduced release R8 missing-class warning noise for optional AndroidX Window vendor APIs and OkHttp Graal host-only stubs
- Upgraded Voyager to 1.1.0-beta03 to fix `IllegalStateException: State is 'DESTROYED'` crash from `AndroidScreenLifecycleOwner` firing after terminal lifecycle state
- Enrichment screen observer no longer gets permanently stuck after a refresh failure; supervisor scope now isolates the observer and refresh coroutines independently
- CrashActivity no longer crashes on launch in the `:error_handler` process due to uninitialized DI
- Discover screen no longer gets stuck in a permanent loading state when the recommendation flow throws an upstream exception
- WorkManager init no longer crashes the `:error_handler` process; periodic sync setup is now guarded to the main process only
- LightNovelPluginManager coroutine scope now cancelled on close, preventing a resource leak
- Removed unused `sqldelight-android-paging` dependency that was downgrading Compose Foundation at runtime and causing `NoSuchMethodError` on `AnchoredDraggableState`
- Enforced Compose Foundation 1.7+ floor as a dependency resolution constraint; fixed `junit-platform-launcher` missing-version build failure on modules without JUnit transitive deps

### Changed

- Migrated Kotlin context receivers to context parameters for Kotlin 2.2+ compatibility
- **Source health check refactor** — shared health check state machine extracted into a reusable abstraction used by manga, anime, and light novel sources

### Other

- Upgraded OkHttp from 5.0.0-alpha.14 to 5.3.2 stable
- Upgraded to AGP 9.0.1, Gradle 9.1, and compileSdk/targetSdk 36
- Upgraded Kotlin to 2.3.10
- Upgraded Gradle plugins to latest compatible versions
- Bumped target SDK to 35
- Updated version bump workflow for 4-part upstream-tracking scheme

## [v0.18.1.1] - Rayniyomi (First Stable Release)

### Added

- PIN lock authentication with SHA-256 salted hashing, escalating timeouts, and secure storage
- Light novel plugin system with complete reading support and dedicated plugin architecture
- LLM-powered manga translation with vision LLM integration and reader toggle
- Translation engines: Claude (Anthropic), OpenAI (GPT-4 Vision), OpenRouter (multi-model), Google (Gemini)
- Automatic webtoon auto-scroll with play/pause, speed controls, and tap-to-pause
- Improved categories with hierarchical organization and search
- List display size slider for adjustable library list density
- Resumable and multi-thread anime downloads with HTTP range resume and 1-4 concurrent connections
- Download priority and "Download Next Unread" mode
- Configurable page download concurrency
- WorkManager auto-retry with battery optimization guidance
- Plugin performance budgets and tracking
- Plugin compatibility governance with version matrix
- Plugin update policy with staged rollout controls
- Plugin offline/network resilience with cached manifests
- Plugin reliability telemetry and alerting
- Plugin data lifecycle and migration hardening
- Plugin security runbook with denylist support
- Consent-first plugin install flow
- Firebase Crashlytics integration for crash monitoring
- StrictMode dev checks for main-thread I/O
- Static guardrails against runBlocking on UI thread

### Improved

- AniSkip hardening — improved intro/outro auto-skip reliability with disk cache
- Entry screen refactoring — shared anime/manga screen components
- Jetpack Compose migration for plugin UI
- Database index optimization for library queries
- Cover image downsampling in grid views
- 50MB OkHttp network cache (up from 5MB)
- Library filtering moved to database layer
- Compose recomposition stabilization
- Deferred Firebase Crashlytics initialization
- Full async/threading refactoring (removed all runBlocking from UI paths)

### Other

- Rebranded from Aniyomi to Rayniyomi (app name, launcher icon, all UI text)
- Changed applicationId to xyz.rayniyomi
- Disabled upstream update checker
- Fork-owned Firebase configuration
- Disabled ACRA crash reporting

## Unreleased

### Added

- Added a description for the horizontal seek gesture setting ([@kenkoro](https://github.com/kenkoro)) ([#2224](https://github.com/aniyomiorg/aniyomi/pull/2224))
- Add long-press speed boost gesture for playback control
- Add Chromecast Cast SDK infrastructure, player integration, and cast UI components

### Fixed

- Swapped keyEvent listeners for left and right keyboard arrow keys as they were swapped in the code causing the opposite of the desired behavior([@alphastark](https://github.com/alphastark)) ([#2219](https://github.com/aniyomiorg/aniyomi/pull/2219))
- Fix some malformed translated strings that made the player quit when Aniskip was enabled ([@686udjie](https://github.com/686udjie)) ([#2217](https://github.com/aniyomiorg/aniyomi/pull/2217))
- Apply Google Services plugin only for release builds
- Resolve startup crashes by properly registering `OkHttpClient` in DI
- Fix app icon background rendering for launcher assets
- Harden version bump automation to correctly preserve fork versioning scheme
- Reduce crash risk and resource issues in notifications/download/backup flows (bitmap leak removal, safer streams, and defensive coroutine/error handling)
- Fix migration chain behavior so failed migrations do not silently pass
- Guard error-log notification URI creation when error-log files cannot be written

### Changed

- Migrate Kotlin context receivers to context parameters for Kotlin 2.2 compatibility

### Improved

- Improve local and CI build performance with Gradle configuration cache, Kotlin daemon tuning, and CI caching/parallelism updates

### Other

- Prune README content to concise bullet points
- Realign and document fork versioning workflow and housekeeping for automated bumps
- Update Rayniyomi launcher icon branding assets

## [v0.18.1.2] - 2025-10-28
### Fixed

- Fix Hosters feature detection (again) ([@hollowshiroyuki](https://github.com/hollowshiroyuki)) ([#2216](https://github.com/aniyomiorg/aniyomi/pull/2216))

## [v0.18.1.1] - 2025-10-26
### Fixed

- Fix source Seasons/Hosters feature detection ([@hollowshiroyuki](https://github.com/hollowshiroyuki)) ([#2195](https://github.com/aniyomiorg/aniyomi/pull/2195))
- Fix shared download cache messing up downloaded episodes detection ([@choppeh](https://github.com/choppeh)) ([#2184](https://github.com/aniyomiorg/aniyomi/pull/2184))
- Fix Shikimori anime tracking ([@danya140](https://github.com/danya140)) ([#2205](https://github.com/aniyomiorg/aniyomi/pull/2205))

### Improved

- Make volume gesture the same sensitivity as brightness ([@jmir1](https://github.com/jmir1))

## [v0.18.1.0] - 2025-10-02
### Fixed

- Fix list view resetting scroll upon exiting child ([@quickdesh](https://github.com/quickdesh)) ([#1982](https://github.com/aniyomiorg/aniyomi/pull/1982))
- Fix episode number parsing ([@Secozzi](https://github.com/Secozzi)) ([#2096](https://github.com/aniyomiorg/aniyomi/pull/2096))
- Fix tracking menu not opening on add to library ([@Secozzi](https://github.com/Secozzi)) ([#2098](https://github.com/aniyomiorg/aniyomi/pull/2098))
- Fix stop/continue anime download button ([@Secozzi](https://github.com/Secozzi)) ([#2099](https://github.com/aniyomiorg/aniyomi/pull/2099))
- Fix creating/restoring backups between mihon and aniyomi ([@Secozzi](https://github.com/Secozzi)) ([#2117](https://github.com/aniyomiorg/aniyomi/pull/2117))

### Added

- Add support for new parameters from ext lib 16 ([@quickdesh](https://github.com/quickdesh)) ([#1982](https://github.com/aniyomiorg/aniyomi/pull/1982))
- Add player settings to the main settings screen ([@jmir1](https://github.com/jmir1)) ([#2081](https://github.com/aniyomiorg/aniyomi/pull/2081))
- Add seasons support ([@Secozzi](https://github.com/Secozzi)) ([#2095](https://github.com/aniyomiorg/aniyomi/pull/2095))

## [v0.18.0.1] - 2025-07-06
### Fixed

- Fix crash on migration ([@Secozzi](https://github.com/Secozzi)) ([#2079](https://github.com/aniyomiorg/aniyomi/pull/2079))

## [v0.18.0.0] - 2025-07-05
### Added

- Set mpv's media-title property ([@Secozzi](https://github.com/Secozzi)) ([#1672](https://github.com/aniyomiorg/aniyomi/pull/1672))
- Add mpvKt to external players ([@Secozzi](https://github.com/Secozzi)) ([#1674](https://github.com/aniyomiorg/aniyomi/pull/1674))
- Add video filters ([@abdallahmehiz](https://github.com/abdallahmehiz)) ([#1698](https://github.com/aniyomiorg/aniyomi/pull/1698))
- Show hours and minutes in relative time strings ([@jmir1](https://github.com/jmir1)) ([`1f3be7b`](https://github.com/aniyomiorg/aniyomi/commit/1f3be7b523136039b3b60213f2cee7959a9367d7))
  - Fix some issues with relative date calculations ([@jmir1](https://github.com/jmir1)) ([`03e1ecd`](https://github.com/aniyomiorg/aniyomi/commit/03e1ecd75edd2ea15dc8732ffeab32c6af26b202))
- Add better auto sub select ([@Secozzi](https://github.com/Secozzi)) ([#1706](https://github.com/aniyomiorg/aniyomi/pull/1706))
- Copy the file location when using ext downloader ([@quickdesh](https://github.com/quickdesh)) ([#1758](https://github.com/aniyomiorg/aniyomi/pull/1758))
- Replace player with mpvKt ([@Secozzi](https://github.com/Secozzi)) ([#1834](https://github.com/aniyomiorg/aniyomi/pull/1834), [#1855](https://github.com/aniyomiorg/aniyomi/pull/1855), [#1859](https://github.com/aniyomiorg/aniyomi/pull/1859), [#1860](https://github.com/aniyomiorg/aniyomi/pull/1860))
  - Move player preferences to separate section ([@Secozzi](https://github.com/Secozzi)) ([#1819](https://github.com/aniyomiorg/aniyomi/pull/1819))
- Implement video hosters ([@Secozzi](https://github.com/Secozzi)) ([#1892](https://github.com/aniyomiorg/aniyomi/pull/1892))
- Add size slider for the "List Display" Mode ([@MavikBow](https://github.com/MavikBow)) ([#1906](https://github.com/aniyomiorg/aniyomi/pull/1906))
  - Make the default list a set size and make browse list scale ([@MavikBow](https://github.com/MavikBow)) ([#1914](https://github.com/aniyomiorg/aniyomi/pull/1914))
- Allow negative brightness values (dimming) ([@jmir1](https://github.com/jmir1)) ([#1915](https://github.com/aniyomiorg/aniyomi/pull/1915))
- Add new lua functions for custom buttons ([@Secozzi](https://github.com/Secozzi)) ([#1980](https://github.com/aniyomiorg/aniyomi/pull/1980))
- Use timestamps provided by extensions ([@Secozzi](https://github.com/Secozzi)) ([#1983](https://github.com/aniyomiorg/aniyomi/pull/1983))
- Add titles to player sheets + consistency with More sheet ([@quickdesh](https://github.com/quickdesh)) ([#2015](https://github.com/aniyomiorg/aniyomi/pull/2015))
- Add script & script-opts editor to player settings ([@Secozzi](https://github.com/Secozzi)) ([#2019](https://github.com/aniyomiorg/aniyomi/pull/2019))

### Improved

- Show "Now" instead of "0 minutes ago" ([@Secozzi](https://github.com/Secozzi)) ([#1715](https://github.com/aniyomiorg/aniyomi/pull/1715))
- Add headers when using 1dm as external player ([@Secozzi](https://github.com/Secozzi)) ([#2032](https://github.com/aniyomiorg/aniyomi/pull/2032))

### Fixed

- Fix enhanced tracking for jellyfin ([@Secozzi](https://github.com/Secozzi)) ([#1656](https://github.com/aniyomiorg/aniyomi/pull/1656), [#1658](https://github.com/aniyomiorg/aniyomi/pull/1658))
- Use different status strings for anime trackers ([@jmir1](https://github.com/jmir1)) ([`74b32a3`](https://github.com/aniyomiorg/aniyomi/commit/74b32a3a0b323ed2f6f7929e131dcb4901e7bf9b))
- Fix Shikimori tracking for anime ([@jmir1](https://github.com/jmir1)) ([`58817c7`](https://github.com/aniyomiorg/aniyomi/commit/58817c724e2808072ff273329cee261d12084927))
- Group updates by date and not time ([@jmir1](https://github.com/jmir1)) ([`c83ebf3`](https://github.com/aniyomiorg/aniyomi/commit/c83ebf322f48d41ca1ad0105262160ecb7cde991))
- Fix airing time not showing ([@Secozzi](https://github.com/Secozzi)) ([#1720](https://github.com/aniyomiorg/aniyomi/pull/1720))
- Don't invalidate anime downloads on startup ([@Secozzi](https://github.com/Secozzi)) ([#1753](https://github.com/aniyomiorg/aniyomi/pull/1753))
- Fix hidden categories getting reset after delete/reorder ([@cuong-tran](https://github.com/cuong-tran)) ([#1780](https://github.com/aniyomiorg/aniyomi/pull/1780))
- Fix episode progress not being saved and duplicate tracks ([@perokhe](https://github.com/perokhe)) ([#1784](https://github.com/aniyomiorg/aniyomi/pull/1784), [#1785](https://github.com/aniyomiorg/aniyomi/pull/1785))
- Fix subtitle select not matching two letter language codes ([@Secozzi](https://github.com/Secozzi)) ([#1805](https://github.com/aniyomiorg/aniyomi/pull/1805))
- Fix potential intent extra npe ([@quickdesh](https://github.com/quickdesh)) ([#1816](https://github.com/aniyomiorg/aniyomi/pull/1816))
- Fix history date header duplication ([@quickdesh](https://github.com/quickdesh)) ([#1817](https://github.com/aniyomiorg/aniyomi/pull/1817))
- Fix migrations not getting context correctly ([@Secozzi](https://github.com/Secozzi)) ([#1820](https://github.com/aniyomiorg/aniyomi/pull/1820))
- Fix various issues due to replacing the player with mpvKt
  - Fix gesture seeking not seeking to start and end ([@perokhe](https://github.com/perokhe)) ([#1865](https://github.com/aniyomiorg/aniyomi/pull/1865))
  - Fix crash when opening player settings in tablet ui ([@Secozzi](https://github.com/Secozzi)) ([#1868](https://github.com/aniyomiorg/aniyomi/pull/1868))
  - Fix episode list in player not respecting filters & crash when exiting while stuff is loading ([@Secozzi](https://github.com/Secozzi)) ([#1869](https://github.com/aniyomiorg/aniyomi/pull/1869))
  - Fix episode being marked as seen at start ([@perokhe](https://github.com/perokhe)) ([#1871](https://github.com/aniyomiorg/aniyomi/pull/1871))
  - Fix player not being paused when loading tracks after changing quality ([@Secozzi](https://github.com/Secozzi)) ([#1878](https://github.com/aniyomiorg/aniyomi/pull/1878))
  - Fix lag when toggling player ui ([@Secozzi](https://github.com/Secozzi)) ([#1887](https://github.com/aniyomiorg/aniyomi/pull/1887))
  - Fix audio selection not working on external audio tracks ([@Secozzi](https://github.com/Secozzi)) ([#1901](https://github.com/aniyomiorg/aniyomi/pull/1901))
  - Reset "hide player controls time" when pressing custom button ([@Secozzi](https://github.com/Secozzi)) ([#1902](https://github.com/aniyomiorg/aniyomi/pull/1902))
  - Don't unpause on share and save ([@Secozzi](https://github.com/Secozzi)) ([#1905](https://github.com/aniyomiorg/aniyomi/pull/1905))
  - Fix player pausing with gesture seek ([@perokhe](https://github.com/perokhe)) ([#1916](https://github.com/aniyomiorg/aniyomi/pull/1916))
  - Fix potential npe issues with mpv-lib ([@Secozzi](https://github.com/Secozzi)) ([#1921](https://github.com/aniyomiorg/aniyomi/pull/1921))
  - Dismiss chapter sheet on chapter select ([@Secozzi](https://github.com/Secozzi)) ([#1976](https://github.com/aniyomiorg/aniyomi/pull/1976))
  - Fix some issues caused by [`10e28cc`](https://github.com/aniyomiorg/aniyomi/commit/10e28cc4092758cf38d27cc14aadf539698738f2) ([@Secozzi](https://github.com/Secozzi)) ([#1981](https://github.com/aniyomiorg/aniyomi/pull/1981))
  - Fix npe issue caused in player controls ([@Secozzi](https://github.com/Secozzi)) ([#1986](https://github.com/aniyomiorg/aniyomi/pull/1986))
- Replace some manga strings with respective anime strings ([@perokhe](https://github.com/perokhe)) ([#1864](https://github.com/aniyomiorg/aniyomi/pull/1864))
- Open correct tab from extension update notifications ([@jmir1](https://github.com/jmir1)) ([`161471d`](https://github.com/aniyomiorg/aniyomi/commit/161471d94a2350c0c983eeeccd3b7ac0dc66d429))
- Fix sub-auto not loading all external subtitle files ([@perokhe](https://github.com/perokhe)) ([#1866](https://github.com/aniyomiorg/aniyomi/pull/1866))
- Fix `ALSearchItem.format` nullability ([@Secozzi](https://github.com/Secozzi)) ([#1910](https://github.com/aniyomiorg/aniyomi/pull/1910))
- Don't format mpv preferences ([@Secozzi](https://github.com/Secozzi)) ([#1939](https://github.com/aniyomiorg/aniyomi/pull/1939))
- Prevent crash on app death when watching in external player ([@Secozzi](https://github.com/Secozzi)) ([#1945](https://github.com/aniyomiorg/aniyomi/pull/1945))
- Don't run unnecessary stuff when exiting the player ([@Secozzi](https://github.com/Secozzi)) ([#1961](https://github.com/aniyomiorg/aniyomi/pull/1961))
- Fix some downloader issues ([@Secozzi](https://github.com/Secozzi)) ([#1964](https://github.com/aniyomiorg/aniyomi/pull/1964))
  - Fix downloader not working for certain types of tracks & duration sometimes not being logged ([@Secozzi](https://github.com/Secozzi)) ([#2001](https://github.com/aniyomiorg/aniyomi/pull/2001))
- Fix some issues with intro skip length ([@jmir1](https://github.com/jmir1)) ([`72cac57`](https://github.com/aniyomiorg/aniyomi/commit/72cac57d8e66366cbc0f3106eb351c82250c460b), [`25dd3ea`](https://github.com/aniyomiorg/aniyomi/commit/25dd3ea69fb217de7b0485c29e4a9b970737fd45))
- Force clipboard to use UI thread when copying path for external players ([@quickdesh](https://github.com/quickdesh)) ([#1994](https://github.com/aniyomiorg/aniyomi/pull/1994))
- Use application directory for storing files used by mpv ([@Secozzi](https://github.com/Secozzi)) ([#1995](https://github.com/aniyomiorg/aniyomi/pull/1995))
- Update backup warning string (follow Mihon) ([@cuong-tran](https://github.com/cuong-tran)) ([#2012](https://github.com/aniyomiorg/aniyomi/pull/2012))
- Fix issues with episode deletion & more ([@quickdesh](https://github.com/quickdesh)) ([#2017](https://github.com/aniyomiorg/aniyomi/pull/2017))
- Fix vertical slider width issues and shift boost volume value to slider ([@quickdesh](https://github.com/quickdesh)) ([#2018](https://github.com/aniyomiorg/aniyomi/pull/2018))
- Fix MyAnimeList login ([@choppeh](https://github.com/choppeh)) ([#2035](https://github.com/aniyomiorg/aniyomi/pull/2035))
- Call sort methods for videos and hosters ([@cuong-tran](https://github.com/cuong-tran)) ([#2058](https://github.com/aniyomiorg/aniyomi/pull/2058))
- Invalidate preferred languages in settings ([@Secozzi](https://github.com/Secozzi)) ([#2075](https://github.com/aniyomiorg/aniyomi/pull/2075))
- Fix crash when using sort by airing time ([@quickdesh](https://github.com/quickdesh)) ([#2076](https://github.com/aniyomiorg/aniyomi/pull/2076))

### Other

- Merge from mihon until 0.16.5 ([@Secozzi](https://github.com/Secozzi)) ([#1663](https://github.com/aniyomiorg/aniyomi/pull/1663))
  - Merge until latest mihon commits ([@Secozzi](https://github.com/Secozzi)) ([#1693](https://github.com/aniyomiorg/aniyomi/pull/1693))
  - Merge until latest mihon commits (v0.17.0) ([@Secozzi](https://github.com/Secozzi)) ([#1804](https://github.com/aniyomiorg/aniyomi/pull/1804))
  - Merge until latest mihon commits (v0.18.0) ([@Secozzi](https://github.com/Secozzi)) ([#1863](https://github.com/aniyomiorg/aniyomi/pull/1863))
- Remove ACRA crash report analytics ([@jmir1](https://github.com/jmir1)) ([`d3c6a15`](https://github.com/aniyomiorg/aniyomi/commit/d3c6a159d82ca239c10e8f5822c3b2046c5545f2), [`5ae35c8`](https://github.com/aniyomiorg/aniyomi/commit/5ae35c891b90ae927200185641240280effaf667))

## [v0.16.4.3] - 2024-07-01
### Fixed

- Fix extensions disappearing due to errors with the ClassLoader ([@jmir1](https://github.com/jmir1)) ([`959f84a`](https://github.com/aniyomiorg/aniyomi/commit/959f84ab41859f90c458c076d83d363ae086e47f))

## [v0.16.4.2] - 2024-07-01
### Fixed

- Hotfix to eliminate all proguard issues causing errors and crashes ([@jmir1](https://github.com/jmir1)) ([`a8cd723`](https://github.com/aniyomiorg/aniyomi/commit/a8cd7233dfdf26c98ff86b1871a7ac5774379b5e), [`a7644c2`](https://github.com/aniyomiorg/aniyomi/commit/a7644c268153fc0b9f10c27202591f960c6f6384), [`5045fa1`](https://github.com/aniyomiorg/aniyomi/commit/5045fa18ce5a1faa2130f1a33609e43d8453f078))

## [v0.16.4.1] - 2024-07-01
### Fixed

- Hotfix release to address errors with extensions ([@jmir1](https://github.com/jmir1)) ([`98d2528`](https://github.com/aniyomiorg/aniyomi/commit/98d252866e17beba7d9a4d094797e23c05ead6c1))

## [v0.16.4.0] - 2024-07-01
### Fixed

- Fix pip not broadcasting intent in A14+ ([@quickdesh](https://github.com/quickdesh)) ([#1603](https://github.com/aniyomiorg/aniyomi/pull/1603))
- Fix advanced player settings crash in android ≤ 10 ([@perokhe](https://github.com/perokhe)) ([#1627](https://github.com/aniyomiorg/aniyomi/pull/1627))

### Improved

- Hide the skip intro button if the skipped amount == 0 ([@abdallahmehiz](https://github.com/abdallahmehiz)) ([#1598](https://github.com/aniyomiorg/aniyomi/pull/1598))

### Other

- Merge from mihon until mihon 0.16.2 ([@Secozzi](https://github.com/Secozzi)) ([#1578](https://github.com/aniyomiorg/aniyomi/pull/1578))
  - Merge from mihon until 0.16.4 ([@Secozzi](https://github.com/Secozzi)) ([#1601](https://github.com/aniyomiorg/aniyomi/pull/1601))
