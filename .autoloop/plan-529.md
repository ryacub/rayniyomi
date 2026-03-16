# Implementation Plan: Resolve rayniyomi TODO/FIXME comments (R529)

## Overview
Audit and resolve all rayniyomi-authored TODO/FIXME comments across 8 source files. Features include: removing dead code stub (rollbackToLastGood), implementing user error feedback for PIN setup, converting deferred TODOs to GitHub issues, and removing obsolete comments. This improves codebase maintainability and unblocks future PIN security enhancements.

## Stack
- Language: Kotlin
- Test framework: JUnit4 + JUnit5 + MockK + Kotest
- Test command: ./gradlew :app:testDebugUnitTest --tests "*LightNovel*" or "./gradlew :app:testDebugUnitTest"

## Features (implement in this order)

### 1. Remove rollbackToLastGood stub
Remove the rollbackToLastGood() method, ROLLBACK_NOT_AVAILABLE enum value, and its branch in SettingsLightNovelScreen.installErrorMessage()

**Files affected:**
- app/src/main/java/eu/kanade/tachiyomi/feature/novel/LightNovelPluginManager.kt (lines 125-137, remove method)
- Check for ROLLBACK_NOT_AVAILABLE enum usage across codebase
- app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsLightNovelScreen.kt (search for when branch handling this error)

**Test file:** n/a (dead code removal — compile check is sufficient, no new tests needed)

**Success criteria:**
- Project compiles cleanly (./gradlew :app:compileDebugKotlin)
- Existing LightNovelPluginManager tests pass (./gradlew :app:testDebugUnitTest --tests "*LightNovel*")
- rollbackToLastGood() method is completely removed
- ROLLBACK_NOT_AVAILABLE enum value is completely removed
- All references to these removed items are eliminated

**Independent:** No (do first, other features independent of it)

### 2. Add PIN setup error feedback
Implement the TODO: Show error message to user in SettingsSecurityScreen by adding error state feedback when PIN setup fails (onSave returns false). Look at existing error patterns in the file to match UI conventions.

**Files affected:**
- app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsSecurityScreen.kt (lines 70-76, add error display)

**Test file:** Create app/src/test/java/eu/kanade/presentation/more/settings/screen/SettingsSecurityScreenTest.kt

**Test structure (TDD approach):**
- Write failing test that verifies error state is set when PIN setup fails
- Implement minimal error state variable and UI display logic
- Verify state clears on next retry attempt

**Success criteria:**
- When PIN setup fails (onSave returns false), user sees error feedback (snackbar or error text)
- Error state clears when user attempts to set PIN again
- Existing rollback logic (disabling PIN lock, clearing partial data) still functions
- Pattern matches existing error handling in SettingsSecurityScreen

**Independent:** Yes

### 3. Strip deferred TODO comments
Remove the remaining TODO comments that represent deferred/future work, converting significant ones to GitHub issues. For each:
- (a) Create a GH issue with gh CLI if it's real future work
- (b) Remove the comment from source

**Comments to process:**
1. LightNovelPluginManager.kt:489 — TODO(R236-P): Replace SHA-256 cert fingerprints
   - Create GH issue: "chore: pin real SHA-256 cert fingerprints in LightNovelPluginManager (R236-P)"
   - Remove comment

2. BrowseTab.kt:56 — TODO: Find a way to let it open Global Anime/Manga Search depending on tab
   - Create GH issue: "feat: improve Browse tab search based on Anime/Manga context"
   - Remove comment

3. AnimeDownloadManager.kt:514 — TODO: Show notification to user about repeated crashes
   - Create GH issue: "feat: add download crash notification when threshold exceeded"
   - Remove comment

4. AnimeDownloadManager.kt:532 — TODO: Show dialog prompting user to disable battery optimization
   - Create GH issue: "feat: add battery optimization prompt for bulk downloads"
   - Remove comment

5. MangaDownloadManager.kt:501 — TODO: Show notification to user about repeated crashes
   - Covered by issue from #3 (same feature for manga)
   - Remove comment

6. MangaDownloadManager.kt:519 — TODO: Show dialog prompting user to disable battery optimization
   - Covered by issue from #4 (same feature for manga)
   - Remove comment

7. PlayerMpvInitializer.kt:151 — TODO: I think this is a bad hack
   - This is subjective commentary, not actionable future work
   - Just delete the comment, no GH issue

**Test file:** n/a (comment removal)

**Success criteria:**
- No rayniyomi-specific TODO/FIXME comments remain in the 8 source files listed
- GitHub issues created for features: 4 new issues (cert fingerprints, search context, crash notification, battery optimization)
- All TODO comment lines removed cleanly
- Code compiles

**Independent:** Yes

## Completion Criteria
- All existing LightNovelPluginManager tests pass (./gradlew :app:testDebugUnitTest --tests "*LightNovel*")
- Project compiles cleanly (./gradlew :app:compileDebugKotlin)
- No rayniyomi-specific TODO/FIXME remain in the 8 source files:
  - app/src/main/java/eu/kanade/tachiyomi/feature/novel/LightNovelPluginManager.kt
  - app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsSecurityScreen.kt
  - app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt
  - app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloadManager.kt
  - app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadManager.kt
  - app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerMpvInitializer.kt
- SettingsSecurityScreen error feedback works as expected (manual verification acceptable)
- 4 GitHub issues created for deferred features

## Edge Cases

### Feature 1 (Remove rollbackToLastGood):
- Check for any @Suppress annotations, javadoc, or test references to rollbackToLastGood
- Ensure ROLLBACK_NOT_AVAILABLE enum removal doesn't break other error handling switches
- May need to search entire codebase for InstallErrorCode.ROLLBACK_NOT_AVAILABLE

### Feature 2 (PIN setup error):
- What if error state is shown but user immediately retries? Clear error on next attempt.
- Look at how other failures are surfaced in SettingsSecurityScreen — match the existing pattern.
- Consider existing Snackbar, LaunchedEffect, or state variable patterns in the composable
- Should not interfere with existing ChangePinDialog flow

### Feature 3 (Strip TODOs):
- Verify that removing crash notification and battery optimization TODOs doesn't leave orphaned logging
- Check if crash counter and battery optimization checks are still functional (they are, just no user feedback)

## Risks & Notes

**Feature 1 risks:**
- rollbackToLastGood() appears in LightNovelPluginManager (method definition lines 125-137) and enum InstallErrorCode somewhere
- ROLLBACK_NOT_AVAILABLE enum usage appears in SettingsLightNovelScreen when branch for installErrorMessage()
- All 3 locations must be updated atomically to avoid compile errors
- Search codebase for "ROLLBACK_NOT_AVAILABLE" and "rollbackToLastGood" before removal

**Feature 2 risks:**
- SettingsSecurityScreen uses Compose, so error feedback must follow Compose patterns (state hoisting, snackbar or error text)
- Must not break existing PIN hash/salt verification logic
- Consider whether error should persist or auto-dismiss

**Feature 3 risks:**
- BrowseTab TODO and DownloadManager TODOs should each get GitHub issues created with `gh issue create --repo ryacub/rayniyomi`
- PlayerMpvInitializer TODO is subjective ("bad hack") — just delete it without an issue
- Verify no other rayniyomi-authored TODOs exist before declaring success

**Pre-removal verification:**
```bash
# Find all TODO/FIXME comments in the 6 main source files
grep -n "TODO\|FIXME" \
  app/src/main/java/eu/kanade/tachiyomi/feature/novel/LightNovelPluginManager.kt \
  app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsSecurityScreen.kt \
  app/src/main/java/eu/kanade/tachiyomi/ui/browse/BrowseTab.kt \
  app/src/main/java/eu/kanade/tachiyomi/data/download/anime/AnimeDownloadManager.kt \
  app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadManager.kt \
  app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerMpvInitializer.kt
```

**Search for dead code references:**
```bash
grep -r "rollbackToLastGood\|ROLLBACK_NOT_AVAILABLE" app/src/
```
