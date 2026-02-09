# Coroutine Scope Policy and Cancellation Contracts

**Version:** 1.0
**Last Updated:** 2026-02-09
**Status:** Active

## Overview

This document defines rayniyomi's app-wide policy for coroutine scope ownership, lifecycle management, and cancellation contracts. Following this policy ensures predictable resource cleanup, prevents memory leaks, and eliminates race conditions from orphaned coroutines.

## Core Principle: Owned Scopes

**Every coroutine launch must be tied to an explicit lifecycle owner.**

Rayniyomi uses **owned scopes**—coroutine scopes with clear ownership and well-defined cancellation boundaries. The owner dictates when launched coroutines are cancelled, ensuring resources are released at the appropriate time.

### Anti-Pattern: Global Scopes

❌ **Deprecated:**
```kotlin
// NEVER do this - coroutines outlive their owner
fun loadData() {
    launchIO { /* work */ }      // Uses GlobalScope implicitly
    launchUI { /* work */ }      // Uses GlobalScope implicitly
}
```

These global helpers (`launchIO`, `launchUI` without a receiver) are **deprecated** and trigger lint warnings. They launch coroutines that are never cancelled, causing:
- Memory leaks (references held indefinitely)
- Race conditions (work continues after UI destruction)
- Wasted CPU/network (work that's no longer needed)

## Scope Selection Matrix

Choose the appropriate scope based on the lifecycle of your component:

| Component Type | Scope to Use | Lifecycle Boundary | When Cancelled |
|----------------|--------------|-------------------|----------------|
| **ViewModel** | `viewModelScope.launchIO { }` | ViewModel cleared | Screen navigation away |
| **Activity** | `lifecycleScope.launchIO { }` | Activity destroyed | Activity finish |
| **Fragment** | `viewLifecycleOwner.lifecycleScope.launchIO { }` | Fragment view destroyed | Fragment detach |
| **Service/Manager** | `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` | Manual (call `scope.cancel()`) | Service stop or app shutdown |
| **BroadcastReceiver** | `val pendingResult = goAsync()` + `launchIO { }` | `pendingResult.finish()` | Work completes |

## 1. ViewModel-Owned Scopes

**When to use:** For UI-driven background work that should be cancelled when the user navigates away.

### Pattern

```kotlin
class MyViewModel : ViewModel() {
    fun loadData() {
        viewModelScope.launchIO {
            // Background work on IO dispatcher
            val data = repository.fetchData()

            // Switch to Main for UI updates (if needed)
            withUIContext {
                _uiState.update { it.copy(data = data) }
            }
        }
    }
}
```

### Cancellation Contract

- **Cancels when:** `ViewModel.onCleared()` is called (typically on back navigation or Activity finish)
- **Propagation:** All child coroutines launched from `viewModelScope` are cancelled
- **Use for:**
  - Loading screen data
  - User-triggered actions (search, refresh)
  - State management flows

### Real Example: `ReaderViewModel`

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt
viewModelScope.launchIO {
    // Load manga chapters - cancelled if user navigates away
    val chapters = getChapters.await(mangaId)
    // ...
}
```

## 2. Activity/Fragment-Owned Scopes

**When to use:** For work tied to Activity or Fragment lifecycle (e.g., responding to UI events, registration tasks).

### Pattern

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launchIO {
            // Work cancelled when Activity is destroyed
            registerForNotifications()
        }
    }
}
```

### Cancellation Contract

- **Cancels when:** `Activity.onDestroy()` or `Fragment.onDestroyView()` is called
- **Propagation:** All child coroutines launched from `lifecycleScope` are cancelled
- **Use for:**
  - One-time setup tasks
  - Event registration/unregistration
  - Activity-specific background work

### Real Example: `MainActivity`

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt
lifecycleScope.launchIO {
    // Check for app updates - cancelled if activity destroyed
    checkForUpdates()
}
```

## 3. Manager-Owned Scopes

**When to use:** For long-lived services or managers that outlive individual screens (e.g., download managers, cache managers).

### Pattern

```kotlin
class DownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startDownloads() {
        scope.launch {
            // Long-running download work
            processDownloadQueue()
        }
    }

    fun shutdown() {
        scope.cancel()  // Explicit cancellation on service stop
    }
}
```

### Cancellation Contract

- **Cancels when:** Explicitly call `scope.cancel()` (e.g., in service `onDestroy()`)
- **Propagation:** All child coroutines launched from `scope` are cancelled
- **Important:** Use `SupervisorJob()` to isolate failures—one child coroutine's exception won't cancel siblings
- **Use for:**
  - Background services (downloads, library updates)
  - Cache management
  - Long-lived business logic

### Real Examples

#### Download Manager with SupervisorJob

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadManager.kt
class MangaDownloadManager(
    private val context: Context,
    // ...
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startDownloads() {
        scope.launch {
            // Process download queue - cancelled on shutdown
            // SupervisorJob ensures one failed download doesn't cancel others
        }
    }
}
```

#### Cache Manager with Simple Scope

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/data/download/manga/MangaDownloadCache.kt
class MangaDownloadCache(
    private val context: Context,
    // ...
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun invalidateCache() {
        scope.launch {
            // Rebuild cache index - cancelled on app shutdown
        }
    }
}
```

**When to use `SupervisorJob()`:**
- Multiple independent coroutines that should not affect each other
- Failure isolation is critical (e.g., one download failing shouldn't cancel others)

**When to use plain `Dispatchers.IO`:**
- Single coroutine or tightly coupled work
- Failure of any child should cancel the entire operation

## 4. BroadcastReceiver-Owned Scopes

**When to use:** For async work inside `BroadcastReceiver.onReceive()` (which is limited to 10 seconds on main thread).

### Pattern

```kotlin
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_OPEN_CHAPTER -> {
                val pendingResult = goAsync()  // Extends receiver lifetime
                openChapter(context, mangaId, chapterId, pendingResult)
            }
        }
    }

    private fun openChapter(context: Context, mangaId: Long, chapterId: Long, pendingResult: PendingResult) {
        launchIO {
            try {
                // Async work off main thread
                val manga = getManga.await(mangaId)
                val chapter = getChapter.await(chapterId)

                withUIContext {
                    // Start activity on main thread
                    context.startActivity(intent)
                }
            } finally {
                pendingResult.finish()  // Signal completion
            }
        }
    }
}
```

### Cancellation Contract

- **Cancels when:** `pendingResult.finish()` is called (must be called within ~10 seconds)
- **Propagation:** Uses `ProcessLifecycleOwner.lifecycleScope` under the hood (app process lifecycle)
- **Use for:**
  - Notification actions
  - Intent handlers requiring async work
  - Broadcast receiver actions with DB/network calls

### Real Example: `NotificationReceiver`

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/data/notification/NotificationReceiver.kt
override fun onReceive(context: Context, intent: Intent) {
    when (intent.action) {
        ACTION_OPEN_CHAPTER -> {
            val pendingResult = goAsync()
            openChapter(context, mangaId, chapterId, pendingResult)
        }
    }
}

private fun openChapter(context: Context, mangaId: Long, chapterId: Long, pendingResult: PendingResult) {
    launchIO {
        try {
            val manga = getManga.await(mangaId)
            val chapter = getChapter.await(chapterId)
            withUIContext {
                context.startActivity(ReaderActivity.newIntent(context, manga.id, chapter.id))
            }
        } finally {
            pendingResult.finish()
        }
    }
}
```

**Key Points:**
- `goAsync()` extends receiver lifetime beyond `onReceive()` return
- `pendingResult.finish()` MUST be called in a `finally` block
- Work must complete within ~10 seconds or the system will kill the receiver

## 5. Activity-Registered Singleton Scopes

**When to use:** For singletons that need to launch work tied to an active Activity's lifecycle (e.g., external player result handlers).

### Pattern

```kotlin
object ExternalIntents {
    private var activityScope: CoroutineScope? = null

    // Called from MainActivity.onResume()
    fun registerActivity(activity: MainActivity, launcher: ActivityResultLauncher<Intent>, scope: CoroutineScope) {
        activityScope = scope
    }

    // Called from MainActivity.onPause()
    fun unregisterActivity() {
        activityScope = null
    }

    fun processResult(result: ActivityResult) {
        activityScope?.launch {
            // Work cancelled when MainActivity is destroyed
            handleExternalPlayerResult(result)
        }
    }
}
```

### Cancellation Contract

- **Cancels when:** The registered Activity is destroyed (via `lifecycleScope`)
- **Propagation:** Work is cancelled if `activityScope` is null or the activity is destroyed
- **Use for:**
  - Singleton services that need Activity lifecycle awareness
  - Result handlers for external Activities

### Real Example: `ExternalIntents`

```kotlin
// app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt
class ExternalIntents {
    private var activeActivity: MainActivity? = null
    private var activityScope: CoroutineScope? = null

    fun registerActivity(activity: MainActivity, launcher: ActivityResultLauncher<Intent>, scope: CoroutineScope) {
        activeActivity = activity
        activityScope = scope
    }

    fun unregisterActivity() {
        activeActivity = null
        activityScope = null
    }

    private fun processExternalPlayerResult(result: ActivityResult) {
        activityScope?.launch {
            // Update episode progress after external player exits
            // Cancelled if MainActivity is destroyed
        } ?: run {
            logcat(LogPriority.WARN) { "No active activity to process result" }
        }
    }
}
```

## Cancellation Contracts Summary

| Scope Owner | Cancellation Trigger | Cleanup Responsibility | Propagation |
|-------------|---------------------|----------------------|-------------|
| `viewModelScope` | `ViewModel.onCleared()` | Automatic | All child coroutines |
| `lifecycleScope` | `Activity.onDestroy()` | Automatic | All child coroutines |
| `private val scope` (Manager) | Manual `scope.cancel()` | **Explicit** (must call in `onDestroy()`) | All child coroutines |
| `goAsync()` + `launchIO` | `pendingResult.finish()` | **Explicit** (must call in `finally`) | Immediate completion |
| Registered Activity scope | Activity destroyed | Automatic (via `lifecycleScope`) | All child coroutines |

## Migration Examples

### Before: Global Launch (Deprecated)

```kotlin
// ❌ Deprecated - no lifecycle awareness
fun loadData() {
    launchIO {
        val data = repository.fetchData()
        withUIContext {
            updateUI(data)
        }
    }
}
```

### After: ViewModel Scope

```kotlin
// ✅ Correct - cancelled when ViewModel is cleared
class MyViewModel : ViewModel() {
    fun loadData() {
        viewModelScope.launchIO {
            val data = repository.fetchData()
            withUIContext {
                updateUI(data)
            }
        }
    }
}
```

### Real Migration: NotificationReceiver (R09)

**Before (R09 PR #136):**
```kotlin
// Used global scope - work could outlive receiver
ACTION_MARK_AS_READ -> {
    markAsRead(urls, mangaId)  // launchIO internally
}
```

**After (R09 PR #136):**
```kotlin
// Uses goAsync() + launchIO with pendingResult lifecycle
ACTION_MARK_AS_READ -> {
    val pendingResult = goAsync()
    markAsRead(urls, mangaId, pendingResult)
}

private fun markAsRead(urls: Array<String>, mangaId: Long, pendingResult: PendingResult) {
    launchIO {
        try {
            // Async DB updates
            updateChapters(urls, mangaId)
        } finally {
            pendingResult.finish()  // Explicit completion signal
        }
    }
}
```

### Real Migration: Download Managers (R10)

**Before (R10 PR #138):**
```kotlin
class AnimeDownloadManager {
    fun startDownloads() {
        launchIO {  // Used global scope
            processQueue()
        }
    }
}
```

**After (R10 PR #138):**
```kotlin
class AnimeDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startDownloads() {
        scope.launch {  // Uses owned scope
            processQueue()
        }
    }

    // Called from service onDestroy()
    fun shutdown() {
        scope.cancel()
    }
}
```

### Real Migration: ExternalIntents (R11)

**Before (R11 PR #137):**
```kotlin
object ExternalIntents {
    fun processResult(result: ActivityResult) {
        launchIO {  // Global scope - no lifecycle awareness
            updateEpisodeProgress(result)
        }
    }
}
```

**After (R11 PR #137):**
```kotlin
object ExternalIntents {
    private var activityScope: CoroutineScope? = null

    fun registerActivity(activity: MainActivity, scope: CoroutineScope) {
        activityScope = scope
    }

    fun processResult(result: ActivityResult) {
        activityScope?.launch {  // Tied to MainActivity lifecycle
            updateEpisodeProgress(result)
        } ?: logcat { "No active activity" }
    }
}
```

## Common Pitfalls

### 1. Forgetting to Cancel Manager Scopes

❌ **Wrong:**
```kotlin
class MyManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startWork() {
        scope.launch { /* work */ }
    }

    // ❌ Missing cancel() - scope leaks when manager is destroyed
}
```

✅ **Correct:**
```kotlin
class MyManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun startWork() {
        scope.launch { /* work */ }
    }

    fun shutdown() {
        scope.cancel()  // ✅ Explicit cancellation
    }
}
```

### 2. Not Using `finally` with `pendingResult.finish()`

❌ **Wrong:**
```kotlin
private fun handleAction(context: Context, pendingResult: PendingResult) {
    launchIO {
        doWork()
        pendingResult.finish()  // ❌ Won't be called if exception occurs
    }
}
```

✅ **Correct:**
```kotlin
private fun handleAction(context: Context, pendingResult: PendingResult) {
    launchIO {
        try {
            doWork()
        } finally {
            pendingResult.finish()  // ✅ Always called
        }
    }
}
```

### 3. Mixing Scopes Unnecessarily

❌ **Wrong:**
```kotlin
class MyViewModel : ViewModel() {
    private val customScope = CoroutineScope(Dispatchers.IO)  // ❌ Unnecessary

    fun loadData() {
        customScope.launch { /* work */ }  // ❌ Won't be cancelled on ViewModel clear
    }
}
```

✅ **Correct:**
```kotlin
class MyViewModel : ViewModel() {
    fun loadData() {
        viewModelScope.launchIO { /* work */ }  // ✅ Uses built-in lifecycle
    }
}
```

## Lint Enforcement

The deprecated global launch helpers are enforced via Kotlin `@Deprecated` annotations:

```kotlin
@Deprecated(
    message = "Use CoroutineScope.launchIO() with an appropriate scope instead of GlobalScope",
    replaceWith = ReplaceWith("scope.launchIO(block)", "tachiyomi.core.common.util.lang.launchIO"),
    level = DeprecationLevel.WARNING,
)
fun launchIO(block: suspend CoroutineScope.() -> Unit): Job { /* ... */ }
```

**To detect usage:**
```bash
# Search for deprecated global launches
grep -r "launchIO\|launchUI" app/src/ | grep -v "viewModelScope\|lifecycleScope\|scope.launch"
```

## Testing Cancellation

Verify that your scopes are properly cancelled:

```kotlin
@Test
fun `test ViewModel scope cancellation`() = runTest {
    val viewModel = MyViewModel()

    // Launch work
    viewModel.loadData()

    // Simulate ViewModel clear
    viewModel.onCleared()

    // Verify coroutines are cancelled
    advanceUntilIdle()
    // Assertions...
}
```

## Decision Tree

```
┌─────────────────────────────────────┐
│ Does work need to outlive screen?  │
└─────────────┬───────────────────────┘
              │
       ┌──────┴──────┐
       │             │
      Yes            No
       │             │
       v             v
┌──────────────┐  ┌─────────────────────┐
│ Service?     │  │ In ViewModel?       │
└──────┬───────┘  └─────┬───────────────┘
       │                │
       v                v
┌──────────────┐  ┌─────────────────────┐
│ Manager      │  │ viewModelScope      │
│ private val  │  │    .launchIO { }    │
│ scope = ...  │  └─────────────────────┘
└──────────────┘
       │
       v
┌──────────────────────────┐
│ BroadcastReceiver?       │
└──────┬───────────────────┘
       │
       v
┌──────────────────────────┐
│ goAsync() +              │
│ launchIO { } +           │
│ pendingResult.finish()   │
└──────────────────────────┘
```

## Related Documentation

- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Android Lifecycle-aware Coroutines](https://developer.android.com/topic/libraries/architecture/coroutines)
- [R08 - Deprecate Global Launch PR #133](https://github.com/ryacub/rayniyomi/pull/133)
- [R09 - NotificationReceiver Migration PR #136](https://github.com/ryacub/rayniyomi/pull/136)
- [R10 - Download Managers Migration PR #138](https://github.com/ryacub/rayniyomi/pull/138)
- [R11 - ExternalIntents Migration PR #137](https://github.com/ryacub/rayniyomi/pull/137)

## Revision History

| Version | Date       | Author          | Changes                          |
|---------|------------|-----------------|----------------------------------|
| 1.0     | 2026-02-09 | worker-r12-haiku | Initial scope policy definition |
