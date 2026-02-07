# R04: Convert manga startDownloadNow to async-safe API Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Remove `runBlocking` from `MangaDownloadManager.startDownloadNow()` by converting it to a suspend function

**Architecture:** Convert the synchronous `startDownloadNow` function to a suspend function and update all call sites to launch it in appropriate coroutine scopes. The function already calls a suspend function (`MangaDownload.fromChapterId`) but incorrectly wraps it in `runBlocking`, which blocks the calling thread.

**Tech Stack:** Kotlin Coroutines, Android ViewModel (screenModelScope)

**Risk Tier:** T2 (medium risk - multi-file behavior change affecting download pipeline)

---

## Task 1: Convert MangaDownloadManager.startDownloadNow to suspend function

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadManager.kt:113-123`

**Step 1: Change function signature to suspend**

Replace the blocking implementation with a proper suspend function:

```kotlin
// BEFORE (line 113-123):
fun startDownloadNow(chapterId: Long) {
    val existingDownload = getQueuedDownloadOrNull(chapterId)
    // If not in queue try to start a new download
    val toAdd = existingDownload ?: runBlocking { MangaDownload.fromChapterId(chapterId) } ?: return
    queueState.value.toMutableList().apply {
        existingDownload?.let { remove(it) }
        add(0, toAdd)
        reorderQueue(this)
    }
    startDownloads()
}

// AFTER:
suspend fun startDownloadNow(chapterId: Long) {
    val existingDownload = getQueuedDownloadOrNull(chapterId)
    // If not in queue try to start a new download
    val toAdd = existingDownload ?: MangaDownload.fromChapterId(chapterId) ?: return
    queueState.value.toMutableList().apply {
        existingDownload?.let { remove(it) }
        add(0, toAdd)
        reorderQueue(this)
    }
    startDownloads()
}
```

**Step 2: Verify compilation fails at call sites**

Run: `./gradlew :app:compileDebugKotlin`

Expected: Compilation errors at:
- `MangaScreenModel.kt:675`
- `MangaUpdatesScreenModel.kt:184`

This confirms we've found all call sites.

**Step 3: Commit the signature change**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadManager.kt
git commit -m "refactor(downloads): convert MangaDownloadManager.startDownloadNow to suspend (R04)"
```

---

## Task 2: Update MangaScreenModel call site

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/MangaScreenModel.kt:672-678`

**Step 1: Launch suspend call in existing coroutine scope**

The call site is already inside `screenModelScope.launchNonCancellable`, so we can call the suspend function directly:

```kotlin
// BEFORE (line 672-678):
screenModelScope.launchNonCancellable {
    if (startNow) {
        val chapterId = chapters.singleOrNull()?.id ?: return@launchNonCancellable
        downloadManager.startDownloadNow(chapterId)
    } else {
        downloadChapters(chapters)
    }

// AFTER (no change needed - already in coroutine scope):
screenModelScope.launchNonCancellable {
    if (startNow) {
        val chapterId = chapters.singleOrNull()?.id ?: return@launchNonCancellable
        downloadManager.startDownloadNow(chapterId)  // Now calls suspend function
    } else {
        downloadChapters(chapters)
    }
```

**Note:** No code change needed here - it already runs in a coroutine scope, so the suspend call will work automatically.

**Step 2: Verify compilation succeeds**

Run: `./gradlew :app:compileDebugKotlin`

Expected: Compilation error only remains at `MangaUpdatesScreenModel.kt:184`

**Step 3: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/MangaScreenModel.kt
git commit -m "refactor(downloads): update MangaScreenModel to use suspend startDownloadNow (R04)"
```

---

## Task 3: Update MangaUpdatesScreenModel call site

**Files:**
- Modify: `app/src/main/java/eu/kanade/tachiyomi/ui/updates/manga/MangaUpdatesScreenModel.kt:183-185`

**Step 1: Convert caller to suspend and launch it**

The `startDownloadingNow` private function needs to become a suspend function and be called from a coroutine scope:

```kotlin
// BEFORE (line 183-185):
private fun startDownloadingNow(chapterId: Long) {
    downloadManager.startDownloadNow(chapterId)
}

// AFTER:
private suspend fun startDownloadingNow(chapterId: Long) {
    downloadManager.startDownloadNow(chapterId)
}
```

**Step 2: Find and update all callers of startDownloadingNow**

Run: `grep -n "startDownloadingNow" app/src/main/java/eu/kanade/tachiyomi/ui/updates/manga/MangaUpdatesScreenModel.kt`

Expected output will show where this function is called.

**Step 3: Wrap calls in screenModelScope.launch**

Search for callers and wrap them in coroutine launches. Typically these are in event handlers or UI callbacks.

Example pattern:
```kotlin
// If called from non-suspend context:
screenModelScope.launch {
    startDownloadingNow(chapterId)
}
```

**Step 4: Verify compilation succeeds**

Run: `./gradlew :app:compileDebugKotlin`

Expected: Build succeeds with no errors

**Step 5: Commit**

```bash
git add app/src/main/java/eu/kanade/tachiyomi/ui/updates/manga/MangaUpdatesScreenModel.kt
git commit -m "refactor(downloads): update MangaUpdatesScreenModel to launch suspend startDownloadNow (R04)"
```

---

## Task 4: Run verification and create PR

**Step 1: Build the app**

Run: `./gradlew :app:assembleDebug`

Expected: Build succeeds

**Step 2: Run unit tests if available**

Run: `./gradlew :app:testDebugUnitTest`

Expected: All tests pass (or note if no tests exist for this code path)

**Step 3: Verify no other runBlocking usage in MangaDownloadManager**

Run: `grep -n "runBlocking" app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadManager.kt`

Expected: No matches (or only in comments/docs)

**Step 4: Create PR**

```bash
git push origin codex/r04-async-manga-download
gh pr create --title "refactor: convert manga startDownloadNow to async-safe API (R04)" --body "$(cat <<'EOF'
## Ticket
- ID: R04
- Priority: P1
- Risk Tier: T2
- GitHub Issue: Closes #13

## Objective
Remove `runBlocking` from `MangaDownloadManager.startDownloadNow()` by converting it to a proper suspend function, eliminating thread blocking in the download pipeline.

## Scope
- Converted `MangaDownloadManager.startDownloadNow()` from blocking to suspend function
- Updated call sites in `MangaScreenModel` and `MangaUpdatesScreenModel` to properly launch suspend calls
- Removed `runBlocking` wrapper around `MangaDownload.fromChapterId()`

## Non-goals
- Anime download manager (covered in R05)
- Other threading improvements in download pipeline
- Download queue refactoring

## Files Changed
- `app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadManager.kt`: Changed function signature to suspend, removed runBlocking
- `app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/MangaScreenModel.kt`: Updated to call suspend function (no change needed - already in coroutine scope)
- `app/src/main/java/eu/kanade/tachiyomi/ui/updates/manga/MangaUpdatesScreenModel.kt`: Converted caller to suspend and launched in screenModelScope

## Verification

### Commands Run
\`\`\`bash
./gradlew :app:compileDebugKotlin
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
grep -n "runBlocking" app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadManager.kt
\`\`\`

### Results
- Build: ✓ Success
- Unit tests: ✓ Pass (or note: no tests for this code path)
- runBlocking verification: ✓ No remaining instances in MangaDownloadManager

### Not Tested
- Manual testing of "Download Now" functionality in UI
- Download queue behavior under concurrent operations
- Error handling when chapter/manga not found

## Risk
**T2**: Multi-file behavior change affecting download pipeline. Medium risk because:
- Changes core download API signature
- Affects UI layer call sites
- Download operations are user-visible

Mitigation: Call sites already operate in coroutine scopes, minimal behavior change.

## SLO Impact
Positive: Eliminates thread blocking in download operations, should improve UI responsiveness when starting immediate downloads.

## Rollback
Revert this PR to restore blocking API:
\`\`\`bash
git revert <commit-hash>
\`\`\`
Or manually revert files to previous versions.

## Release Notes
No user-facing changes - internal refactoring to improve threading behavior in download system.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

---

## Summary

**Total commits:** 4
- Signature change (breaks compilation intentionally)
- MangaScreenModel update
- MangaUpdatesScreenModel update
- Verification and PR creation

**Verification checklist:**
- [ ] Build succeeds
- [ ] Unit tests pass (if applicable)
- [ ] No remaining runBlocking in MangaDownloadManager
- [ ] PR created and linked to issue #13

**Follow-up work:**
- R05: Same refactoring for AnimeDownloadManager
- Manual UI testing of download functionality
