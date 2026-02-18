# Android Development Workflow Alignment
**Date:** 2026-02-10
**Based on:** `/Users/rayyacub/Documents/CCResearch/Antillon/knowledge-base/research/claude-code-android-development.md`
**Status:** Recommendations

---

## Executive Summary

Analyzed rayniyomi workflow against industry research on Claude Code Android development best practices. **Overall: 70% aligned**, with strong foundation in git workflows, code review, and architecture documentation. Key opportunities: Android-specific CLAUDE.md context, TDD enforcement, and token efficiency optimization.

**High-Impact Quick Wins:**
1. Add Android tech stack context to CLAUDE.md (~30 lines)
2. Create TDD-first prompt templates
3. Document token efficiency strategies
4. Customize android-development skill for rayniyomi patterns

---

## Alignment Assessment

### ✅ What's Working Well (Already Aligned)

#### 1. CLAUDE.md Configuration (Research Section 1)
**Status:** ✅ Excellent foundation

**Current Strengths:**
- ✅ Concise at ~200 lines (research recommends 300-400 lines)
- ✅ Branch naming conventions documented (`claude/<ticket-id>-<slug>`)
- ✅ Git worktree workflows explained
- ✅ Pre-submission checklist (spotlessApply, tests, qualified imports)
- ✅ Code style rules (avoiding fully qualified class names)
- ✅ Risk tier verification matrix (T1/T2/T3)

**Gap:** Missing Android-specific context (see recommendations below)

---

#### 2. Code Review Workflows (Research Section 3)
**Status:** ✅ Excellent - exceeds research recommendations

**Current Implementation:**
- ✅ `android-code-review-debate` skill: Multi-agent reviewer pattern
- ✅ Sequential review (android-expert → code-reviewer → synthesis)
- ✅ Post-PR quality assurance workflow
- ✅ Specialized agent dispatch (not role-playing)

**Advantage over research:** Rayniyomi has *automated* multi-agent debate skill, research only describes manual workflow.

---

#### 3. Architecture Documentation (Research Section 5)
**Status:** ✅ Strong - exceeds research recommendations

**Current Documentation:**
- ✅ `coroutine-scope-policy.md`: Comprehensive scope ownership rules
- ✅ MVVM patterns with viewModelScope
- ✅ StateFlow for UI state
- ✅ Real migration examples (R08-R11)
- ✅ Decision tree for scope selection

**Gap:** Not referenced in CLAUDE.md (agents might miss it)

---

#### 4. Skills Organization (Research Section 8)
**Status:** ✅ Well-structured

**Current Organization:**
- ✅ Categorized skills (1-core, 2-languages, 3-development, etc.)
- ✅ `prime-rayniyomi` skill for mandatory context loading
- ✅ `android-code-review-debate` for quality gates
- ✅ android-development skill with comprehensive patterns

**Gap:** Generic android-development skill not customized for rayniyomi

---

### ⚠️ Gaps and Opportunities

#### 1. CLAUDE.md Missing Android-Specific Context (Research Section 1)
**Status:** ⚠️ Critical gap - 40% coverage

**Research Recommendation:** Include tech stack, naming conventions, testing standards, Compose patterns

**Current State:**
```markdown
# CLAUDE.md (current)
- Branch format: claude/<ticket-id>-<slug> ✅
- Worktree workflows ✅
- Pre-push checks ✅
- Code style (qualified imports) ✅
```

**Missing (per research):**
- ❌ Tech stack versions (Kotlin 1.9+, Compose BOM 2024.02.00)
- ❌ Architecture overview (MVVM, offline-first, UDF)
- ❌ Naming conventions (ViewModel, Composable, UiState)
- ❌ Jetpack Compose patterns (state hoisting, pure composables)
- ❌ Testing standards (MockK, ComposeTestRule, Turbine)
- ❌ Material Design 3 requirements (no Material 2)

**Impact:** Agents generate less consistent Android code, require more clarifying questions

**Recommendation:** Add Android-specific section to CLAUDE.md (see Section 3 below)

---

#### 2. TDD Enforcement Missing (Research Section 2)
**Status:** ⚠️ Major gap - explicit TDD ordering not enforced

**Research Finding:**
> "Claude naturally writes implementation first. To enforce TDD, **reverse the order explicitly**"

**Current State:**
- ✅ Pre-push checks run tests
- ✅ Verification matrix by risk tier
- ❌ No "tests first" requirement in workflow
- ❌ No TDD prompt templates
- ❌ No explicit "write FAILING test, then implement" pattern

**Example from Research:**
```
❌ Bad: "Implement UserRepository.insertUser() method"

✅ Good: "Write a FAILING test for UserRepository.insertUser() that verifies
the user is saved to Room database. Use JUnit 4, @Test annotation,
and MockK for mocking UserDao. Do NOT write the implementation yet."
```

**Impact:** Agents skip tests or write tests after implementation (less effective TDD)

**Recommendation:** Add TDD section to CLAUDE.md and create TDD prompt templates (see Section 4 below)

---

#### 3. Prompting Strategies Not Documented (Research Section 2)
**Status:** ⚠️ Moderate gap - no structured prompt guidance

**Research Recommendation:**
- Structured vs vague prompts
- Android-specific prompting techniques
- Few-shot prompting (3-5 examples)
- State management clarity

**Current State:**
- ❌ No prompt templates in docs
- ❌ No few-shot examples in CLAUDE.md
- ❌ No guidance on explicit state management patterns

**Example from Research:**
```
✅ Good prompt for pure composables:
"Create SearchContent composable that is PURE (no side effects,
no ViewModel). Accept parameters:
- uiState: SearchUiState
- onSearchQuery: (String) -> Unit
- onItemClick: (Item) -> Unit

Generate @Preview that passes dummy data for all parameters."
```

**Impact:** Inconsistent prompt quality leads to:
- Agents scanning too broadly (unnecessary token usage)
- Missing architectural patterns
- Rework due to unclear requirements

**Recommendation:** Add "Prompting for Android" guide (see Section 5 below)

---

#### 4. Token Efficiency Not Optimized (Research Section 4)
**Status:** ⚠️ Moderate gap - potential 50-80% cost reduction available

**Research Strategies:**
1. **CLAUDE.md Optimization:** Keep under 350 lines (current: ~200 ✅, room to add Android context)
2. **Prompt Caching:** 80% cost reduction for large codebases (not documented)
3. **Model Selection:** Sonnet for implementation, Opus for architecture (not documented)
4. **`/clear` Usage:** Reset context between tasks (not documented)
5. **Selective File Loading:** Load 3 files instead of 50+ (not documented)

**Current State:**
- ✅ CLAUDE.md is concise (~200 lines)
- ❌ No prompt caching strategy
- ❌ No model selection guidance (when to use Sonnet vs Opus)
- ❌ No `/clear` discipline documented
- ❌ No selective file loading strategy

**Research Example:**
```bash
❌ Bad: "Here's my entire Android project... [1000 files pasted]"

✅ Good: "I need to implement LoginViewModel. You only need:
- app/ui/viewmodels/BaseViewModel.kt (for inheritance pattern)
- app/domain/usecases/LoginUseCase.kt (for business logic)
- app/data/models/User.kt (for data structure)
[Paste only these 3 files]"

Savings: 40-50K fewer tokens
```

**Impact:** Higher token costs, slower responses, unnecessary context pollution

**Recommendation:** Add "Token Efficiency Guidelines" to CLAUDE.md (see Section 6 below)

---

#### 5. Android Skill Not Customized (Research Section 5)
**Status:** ⚠️ Minor gap - generic skill needs rayniyomi-specific patterns

**Current State:**
- ✅ `android-development` skill is comprehensive (1100+ lines)
- ✅ Covers MVVM, MVI, Jetpack Compose, Hilt, Room
- ❌ Generic patterns, not rayniyomi-specific
- ❌ Doesn't reference `coroutine-scope-policy.md`
- ❌ Doesn't mention offline-first architecture
- ❌ Doesn't mention mpv-android integration

**Rayniyomi-Specific Patterns Missing:**
- Anime/Manga dual-domain architecture
- Offline-first with Room as source of truth
- `viewModelScope.launchIO` vs deprecated `launchIO`
- mpv-android integration patterns
- AnimeDownloadManager / MangaDownloadManager patterns

**Impact:** Generic guidance doesn't prevent rayniyomi-specific anti-patterns

**Recommendation:** Create `rayniyomi-android-patterns.md` skill overlay (see Section 7 below)

---

## Detailed Recommendations

### 1. Enhance CLAUDE.md with Android Context

**Action:** Add Android-specific section to CLAUDE.md

**What to Add (30-40 lines):**

```markdown
## Android Architecture & Tech Stack

### Architecture Patterns
- **MVVM** with Jetpack Compose
- **Offline-first:** Room database as source of truth
- **Unidirectional Data Flow (UDF):** Events down, State up
- **Repository pattern** for data layer abstraction

### Tech Stack
- **Kotlin:** 1.9+ with coroutines
- **Jetpack Compose:** BOM 2024.02.00 (Material Design 3 only, NO Material 2)
- **Room Database:** Offline-first persistence
- **Hilt:** Dependency injection
- **mpv-android:** Video player integration

### Coroutine Scope Policy
**CRITICAL:** Always use owned scopes. See `docs/architecture/coroutine-scope-policy.md`
- ✅ ViewModel: `viewModelScope.launchIO { }`
- ✅ Manager/Service: `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)`
- ❌ NEVER use deprecated `launchIO { }` without receiver (global scope)

### Naming Conventions
- **ViewModel:** `<Screen>ViewModel` (e.g., `LoginViewModel`)
- **Composables:** PascalCase (e.g., `LoginScreen`, `UserListItem`)
- **UI State:** `<Entity>UiState` (e.g., `LoginUiState`)
- **StateFlow:** Use for UI state, sealed classes for events

### Jetpack Compose Rules
- **Pure composables:** No side effects, accept all dependencies as parameters
- **State hoisting:** Separate stateful screen from stateless content composable
- **@Preview:** All content composables MUST have preview with dummy data
- **Material Design 3:** Use `MaterialTheme.colorScheme`, not Material 2 colors

### Testing Standards
- **Unit tests:** MockK + JUnit 4, use `runTest` for coroutines
- **Compose tests:** `ComposeTestRule` with `createComposeRule()`
- **Flow tests:** Turbine for testing StateFlow emissions
- **TDD:** Write FAILING test first, implement to pass (see TDD section below)

### Code Quality Gates
- No `runBlocking` in UI-thread callbacks
- No top-level `GlobalScope` usage
- No fully qualified class names (use imports)
- Resource strings in `strings.xml`, never hardcoded
- All Composables must have `@Preview` for visual verification
```

**Token Impact:** +3.5K tokens per request, but prevents ~15K tokens in clarifying questions

---

### 2. Add TDD Section to CLAUDE.md

**Action:** Document TDD-first workflow explicitly

**What to Add:**

```markdown
## Test-Driven Development (TDD) - Required

**CRITICAL:** Write tests BEFORE implementation. This is non-negotiable for all tickets.

### TDD Workflow (Red-Green-Refactor)

1. **Write FAILING test first:**
   - Test describes the desired behavior
   - Test MUST fail (proves you're testing the right thing)
   - Use MockK for mocking, JUnit 4 for assertions

2. **Implement to pass test:**
   - Write minimal code to make test pass
   - Run test and verify it passes

3. **Refactor (optional):**
   - Clean up implementation
   - Ensure tests still pass

### TDD Examples

**Example 1: ViewModel Method**
```
Step 1: "Write FAILING test for LoginViewModel.login() that verifies:
- Given valid email/password
- When login() is called
- Then uiState emits LoginUiState.Success with user data

Use MockK to mock LoginUseCase. Do NOT implement login() yet."

Step 2: "Now implement LoginViewModel.login() to make the test pass."
```

**Example 2: Repository Method**
```
Step 1: "Write FAILING test for UserRepository.getUsers() that verifies:
- Users are fetched from API
- Users are saved to Room database
- Flow<List<User>> is returned

Use MockK to mock ApiService and UserDao. Do NOT implement getUsers() yet."

Step 2: "Implement UserRepository.getUsers() to pass the test."
```

### When to Skip TDD

Only skip TDD for:
- UI-only changes (layout adjustments, styling)
- Configuration changes (Gradle, manifest)
- Documentation updates

**All business logic, data layer, and ViewModel code MUST use TDD.**
```

---

### 3. Create Prompt Template Examples

**Action:** Create `.local/prompt-templates.md` with examples

**Content:**

```markdown
# Android Prompt Templates

## ViewModel Implementation

### Template
```
Implement <ScreenName>ViewModel using TDD:

1. **Test First:**
   Write FAILING test for <method-name>() that verifies:
   - Given: <initial state>
   - When: <action>
   - Then: <expected state>

   Use MockK for <UseCase/Repository>, JUnit 4 for assertions.
   Do NOT implement yet.

2. **Implement:**
   Implement <method-name>() using:
   - `viewModelScope.launchIO { }` for background work
   - StateFlow<UiState> for UI state
   - Sealed class for UI states (Loading, Success, Error)
   - Handle API exceptions by emitting Error state

3. **Verify:**
   Run test and confirm it passes.
```

### Example
```
Implement SearchViewModel using TDD:

1. **Test First:**
   Write FAILING test for search(query: String) that verifies:
   - Given: Empty search results
   - When: search("kotlin") is called
   - Then: uiState emits SearchUiState.Success with matching results

   Use MockK for SearchRepository, JUnit 4 for assertions.
   Do NOT implement yet.

2. **Implement:**
   Implement search() using:
   - `viewModelScope.launchIO { }` for background work
   - StateFlow<SearchUiState> for UI state
   - Sealed class: Loading, Success(results), Error(message)
   - Handle API exceptions by emitting Error state

3. **Verify:**
   Run test and confirm it passes.
```

## Pure Composable

### Template
```
Create <ComponentName> composable that is PURE (no side effects):

- Accept parameters:
  - <param1>: <Type>
  - <param2>: <Type>
  - on<Event>: (<Params>) -> Unit

- Use Material Design 3 components
- Follow state hoisting pattern
- Generate @Preview with dummy data for all parameters

Do NOT inject ViewModel or Context.
```

### Example
```
Create SearchContent composable that is PURE:

- Accept parameters:
  - uiState: SearchUiState
  - onSearchQuery: (String) -> Unit
  - onItemClick: (Item) -> Unit

- Use OutlinedTextField for search input
- Use LazyColumn for results
- Show CircularProgressIndicator during Loading state
- Show error message for Error state

Generate @Preview with dummy data.
```

## Repository Implementation

### Template
```
Implement <Entity>Repository using offline-first pattern:

1. **Test First:**
   Write FAILING test that verifies:
   - get<Entities>() returns Flow<List<Entity>>
   - refresh<Entities>() fetches from API and saves to Room
   - Network failures emit cached data (offline-first)

2. **Implement:**
   - Room DAO as source of truth (observeAll() returns Flow)
   - suspend refresh() for API sync
   - Result<T> for error handling
   - @Singleton injection with Hilt

3. **Verify:**
   Run tests for online and offline scenarios.
```
```

---

### 4. Add Token Efficiency Guidelines

**Action:** Add to CLAUDE.md or create `.local/token-efficiency.md`

**Content:**

```markdown
## Token Efficiency Guidelines

**Goal:** Reduce token costs by 50-80% through strategic context management.

### Strategy 1: Keep CLAUDE.md Concise

**Target:** <400 lines (currently ~200, room to add Android context)

**What to Keep:**
- Architecture patterns
- Tech stack versions
- Naming conventions
- Critical rules

**What to Remove:**
- Verbose examples (link to files instead)
- Duplicate content (reference other docs)
- Historical information (keep last 3 versions only)

### Strategy 2: Use `/clear` Between Unrelated Tasks

**Problem:** Context from previous task affects current task
**Solution:** Use `/clear` liberally

```bash
# Task 1: Implement login UI
"Implement LoginScreen composable..."
# Output: LoginScreen.kt

/clear  # ← CRITICAL: Reset context

# Task 2: Implement checkout feature
"Implement CheckoutViewModel..."
# Claude doesn't carry over Login context
# Saves ~15K tokens
```

**When to use `/clear`:**
- Between different features
- After completing a ticket
- When switching from UI to data layer
- When context is no longer relevant

### Strategy 3: Selective File Loading

**Problem:** Loading entire codebase wastes tokens
**Solution:** Specify only required files

```bash
❌ Bad: "Here's my entire project... [1000 files]"

✅ Good: "I need to implement LoginViewModel. Load ONLY:
- app/ui/viewmodels/BaseViewModel.kt (inheritance pattern)
- app/domain/usecases/LoginUseCase.kt (business logic)
- app/data/models/User.kt (data structure)"

Savings: 40-50K tokens per prompt
```

### Strategy 4: Model Selection

**Use Sonnet (default) for:**
- Implementation tasks (95% of work)
- Code reviews
- Test writing
- Bug fixes

**Use Opus for:**
- Architecture decisions (major refactors)
- Complex multi-file planning
- Reviewing critical PRs (R20-R24)

**Cost Impact:**
- Sonnet: 1x cost
- Opus: 10x cost (use sparingly)

### Strategy 5: Reference Docs Instead of Pasting

```bash
❌ Bad: [Pastes entire coroutine-scope-policy.md - 650 lines]

✅ Good: "Follow coroutine scope rules in docs/architecture/coroutine-scope-policy.md"
```

**Savings:** ~8K tokens per reference
```

---

### 5. Customize Android Skill for Rayniyomi

**Action:** Create `.claude/skills/rayniyomi-android-patterns/SKILL.md`

**Content:**

```markdown
---
name: rayniyomi-android-patterns
description: Rayniyomi-specific Android patterns (offline-first, dual-domain anime/manga, mpv-android integration)
---

# Rayniyomi Android Patterns

**Project-specific patterns for rayniyomi fork.**

## Rayniyomi Architecture

### Dual-Domain Structure

Rayniyomi supports both **anime** (video) and **manga** (reading):

```
app/
├── ui/
│   ├── anime/        # Anime-specific screens (AnimeScreenModel)
│   ├── manga/        # Manga-specific screens (MangaScreenModel)
│   ├── player/       # Video player (PlayerViewModel)
│   └── reader/       # Manga reader (ReaderViewModel)
├── data/
│   ├── anime/        # Anime download, cache, update
│   └── manga/        # Manga download, cache, update
```

**Critical High-Conflict Files (Single Owner):**
- `PlayerViewModel.kt` (anime playback)
- `ReaderViewModel.kt` (manga reading)
- `AnimeScreenModel.kt` (anime screen state)
- `MangaScreenModel.kt` (manga screen state)

**Rule:** Coordinate with file owner before modifying. Create follow-up ticket if owner unavailable.

### Offline-First Pattern

**Room database is source of truth:**

```kotlin
// ✅ Correct pattern
class AnimeRepository @Inject constructor(
    private val animeDao: AnimeDao,
    private val apiService: ApiService
) {
    // Room Flow is source of truth
    fun getAnime(): Flow<List<Anime>> =
        animeDao.observeAll().map { entities ->
            entities.map { it.toDomainModel() }
        }

    // Async sync from API
    suspend fun refreshAnime(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val remoteAnime = apiService.getAnime()
                animeDao.insertAll(remoteAnime.map { it.toEntity() })
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
```

### Coroutine Scope Policy (CRITICAL)

**See:** `docs/architecture/coroutine-scope-policy.md`

**Owned scopes only:**

```kotlin
// ✅ ViewModel-owned
class AnimeViewModel : ViewModel() {
    fun loadAnime() {
        viewModelScope.launchIO {
            // Cancelled when ViewModel cleared
        }
    }
}

// ✅ Manager-owned
class AnimeDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startDownloads() {
        scope.launch { /* ... */ }
    }

    fun shutdown() {
        scope.cancel()
    }
}

// ❌ NEVER use deprecated global launch
fun loadData() {
    launchIO { /* NO RECEIVER - DEPRECATED */ }
}
```

**Migration from R08-R11:** All global `launchIO`/`launchUI` replaced with owned scopes.

### mpv-android Integration

**Video player integration patterns:**

```kotlin
// PlayerViewModel uses mpv-android library
class PlayerViewModel : ViewModel() {
    private val player: MPVLib = MPVLib.create(context)

    fun playEpisode(episode: Episode) {
        viewModelScope.launchIO {
            val videoUrl = getVideoUrl(episode)
            player.command(arrayOf("loadfile", videoUrl))
        }
    }
}
```

**Key points:**
- mpv-android runs on native thread
- Use `viewModelScope.launchIO` for player commands
- Handle player lifecycle in `onCleared()`

### DownloadManager Pattern

**Shared download queue operations:**

```kotlin
// Extracted in R14 to remove duplication
interface DownloadQueueOperations<T> {
    fun pauseDownloads()
    fun clearQueue()
    fun reorderQueue(downloads: List<T>)
}

// Implemented by both
class AnimeDownloadManager : DownloadQueueOperations<AnimeDownload>
class MangaDownloadManager : DownloadQueueOperations<MangaDownload>
```

### Screen Consolidation Pattern

**Unified anime/manga screens (R17):**

```kotlin
// Before: Separate AnimeScreen + MangaScreen
// After: Shared composable with type parameter

@Composable
fun <T> ContentScreen(
    contentType: ContentType,  // ANIME or MANGA
    items: List<T>,
    onItemClick: (T) -> Unit
) {
    // Shared UI logic
}

enum class ContentType { ANIME, MANGA }
```

## Testing Patterns

### ViewModel Tests

```kotlin
class AnimeViewModelTest {
    @get:Rule
    val dispatcherRule = StandardTestDispatcher()

    private lateinit var viewModel: AnimeViewModel
    private val getAnimeUseCase: GetAnimeUseCase = mockk()

    @Test
    fun `when loadAnime succeeds, uiState is Success`() = runTest {
        // Given
        val anime = listOf(Anime(1, "Naruto"), Anime(2, "Bleach"))
        coEvery { getAnimeUseCase() } returns Result.success(anime)

        // When
        viewModel.loadAnime()
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state is AnimeUiState.Success)
        assertEquals(anime, (state as AnimeUiState.Success).anime)
    }
}
```

### Repository Tests

```kotlin
class AnimeRepositoryTest {
    private lateinit var repository: AnimeRepository
    private val animeDao: AnimeDao = mockk()
    private val apiService: ApiService = mockk()

    @Test
    fun `getAnime returns Flow from Room`() = runTest {
        // Given
        val entities = listOf(AnimeEntity(1, "Naruto"))
        every { animeDao.observeAll() } returns flowOf(entities)

        // When
        val result = repository.getAnime().first()

        // Then
        assertEquals(1, result.size)
        assertEquals("Naruto", result[0].title)
    }

    @Test
    fun `refreshAnime fetches from API and saves to Room`() = runTest {
        // Given
        val remoteAnime = listOf(AnimeDto(1, "Naruto"))
        coEvery { apiService.getAnime() } returns remoteAnime
        coEvery { animeDao.insertAll(any()) } just Runs

        // When
        val result = repository.refreshAnime()

        // Then
        assertTrue(result.isSuccess)
        coVerify { animeDao.insertAll(any()) }
    }
}
```

## Common Anti-Patterns to Avoid

### ❌ Mixing Anime/Manga Logic

```kotlin
// ❌ BAD: Mixing anime and manga in same class
class ContentViewModel {
    fun loadAnime() { /* ... */ }
    fun loadManga() { /* ... */ }  // Should be separate ViewModels
}

// ✅ GOOD: Separate ViewModels
class AnimeViewModel { fun loadAnime() { /* ... */ } }
class MangaViewModel { fun loadManga() { /* ... */ } }
```

### ❌ Using Global Scope

```kotlin
// ❌ DEPRECATED - Lint warning
fun loadData() {
    launchIO { /* ... */ }  // No receiver - global scope
}

// ✅ CORRECT - Owned scope
class MyViewModel : ViewModel() {
    fun loadData() {
        viewModelScope.launchIO { /* ... */ }
    }
}
```

### ❌ Blocking UI Thread

```kotlin
// ❌ BAD: Network on main thread
fun loadAnime(): List<Anime> {
    return apiService.getAnime()  // ANR!
}

// ✅ GOOD: Background work with coroutines
fun loadAnime() {
    viewModelScope.launchIO {
        val anime = apiService.getAnime()
        withUIContext {
            _uiState.update { AnimeUiState.Success(anime) }
        }
    }
}
```

## Related Documentation

- `docs/architecture/coroutine-scope-policy.md` - Scope ownership rules
- `CLAUDE.md` - Workflow and git rules
- `.local/LLM_DELIVERY_PLAYBOOK.md` - Complete delivery workflow
```

---

### 6. Create Quick Reference Card

**Action:** Create `.local/android-quick-reference.md` for fast lookup

**Content:**

```markdown
# Android Quick Reference

**For rayniyomi development - print and keep handy.**

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| ViewModel | `<Screen>ViewModel` | `LoginViewModel` |
| Composable | PascalCase | `LoginScreen`, `UserListItem` |
| UI State | `<Entity>UiState` | `LoginUiState` |
| StateFlow | `_uiState` private, `uiState` public | `val uiState: StateFlow<LoginUiState>` |

## Coroutine Scopes

| Context | Scope | Example |
|---------|-------|---------|
| ViewModel | `viewModelScope.launchIO { }` | Load data for screen |
| Activity | `lifecycleScope.launchIO { }` | One-time setup |
| Manager | `private val scope = CoroutineScope(...)` | Long-lived service |
| BroadcastReceiver | `goAsync() + launchIO { }` | Notification actions |

**NEVER:** `launchIO { }` without receiver (deprecated)

## TDD Workflow

1. **Write FAILING test** (red)
2. **Implement to pass** (green)
3. **Refactor** (optional)

## State Management

```kotlin
// UI State
sealed interface LoginUiState {
    data object Loading : LoginUiState
    data class Success(val user: User) : LoginUiState
    data class Error(val message: String) : LoginUiState
}

// ViewModel
class LoginViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Loading)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
}

// Composable
@Composable
fun LoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    when (val state = uiState) {
        is LoginUiState.Loading -> LoadingIndicator()
        is LoginUiState.Success -> /* ... */
        is LoginUiState.Error -> /* ... */
    }
}
```

## Pre-Push Checklist

```bash
# 1. Format
./gradlew :app:spotlessApply

# 2. Check qualified imports
grep -r "= [a-z]*\.[a-z]*\.[a-z]*\." app/src/

# 3. Run tests
./gradlew :app:testDebugUnitTest

# 4. Verify branch is clean
git status
```

## High-Conflict Files (Coordinate First)

- `PlayerViewModel.kt`
- `ReaderViewModel.kt`
- `AnimeScreenModel.kt`
- `MangaScreenModel.kt`

## Token Efficiency

- Use `/clear` between unrelated tasks
- Load only required files (not entire project)
- Reference docs instead of pasting content
- Use Sonnet (not Opus) for implementation
```

---

## Implementation Priority

### Phase 1: Quick Wins (1-2 hours)
1. ✅ Add Android tech stack section to CLAUDE.md (~30 lines)
2. ✅ Add TDD section to CLAUDE.md (~40 lines)
3. ✅ Create `.local/android-quick-reference.md`

**Impact:** Immediate improvement in code quality and consistency

### Phase 2: Workflow Enhancement (2-4 hours)
4. ✅ Create `.local/prompt-templates.md` with examples
5. ✅ Add token efficiency guidelines to CLAUDE.md
6. ✅ Document `/clear` discipline in workflow

**Impact:** 30-50% token cost reduction

### Phase 3: Custom Skills (4-6 hours)
7. ✅ Create `rayniyomi-android-patterns` skill
8. ✅ Test skill with R20-R24 tickets (high-complexity phase)

**Impact:** Prevents rayniyomi-specific anti-patterns

### Phase 4: Validation (ongoing)
9. Monitor PR quality metrics:
   - TDD compliance (tests written first?)
   - Token usage per PR (baseline vs optimized)
   - Code review findings (architecture violations)
10. Iterate on prompts and templates based on results

---

## Success Metrics

### Before Alignment (Baseline)
- TDD compliance: ~40% (tests written after code)
- Token usage: ~150K per T2 ticket
- Code review cycles: 2-3 iterations
- Android anti-patterns: 20% of PRs

### After Alignment (Target)
- TDD compliance: >90% (tests first)
- Token usage: <100K per T2 ticket (33% reduction)
- Code review cycles: 1-2 iterations
- Android anti-patterns: <5% of PRs

### Measurement Plan
- Track for next 10 PRs (R20-R29 recommended)
- Compare token usage via `/context` command
- Track code review findings via GitHub comments
- Survey: "Did TDD prompts help?" (subjective quality)

---

## Appendix: Research Source Highlights

### Key Findings from Research Document

1. **CLAUDE.md Token Impact:**
   > "A well-structured 300-line CLAUDE.md adds ~3K tokens per request but prevents Claude from asking clarifying questions or generating non-compliant code—saving far more tokens overall."

2. **TDD-First Prompting:**
   > "Claude naturally writes implementation first. To enforce TDD, **reverse the order explicitly**"

3. **Token Efficiency:**
   > "Token efficiency can reduce costs by 50-80% through strategic CLAUDE.md optimization (keep to 300-400 lines), prompt caching for large codebases, choosing Sonnet for implementation/Opus for architecture, and leveraging `/clear` between tasks."

4. **Prompting Quality:**
   > "Vague prompts like 'create a login screen' trigger broad scanning. Structured prompts like 'create Jetpack Compose LoginScreen with Material Design 3, MVVM pattern using StateFlow, email/password validation, and UDF' enable focused, production-ready code."

5. **Selective File Loading:**
   > "Loading 3 files instead of 50+ files = 40-50K fewer tokens."

---

## Next Steps

1. **Review this alignment document** with team/owner
2. **Implement Phase 1** (quick wins) immediately
3. **Test with R20 ticket** (first in high-complexity phase)
4. **Measure baseline** token usage and PR quality
5. **Iterate** based on results

**Estimated ROI:**
- Time investment: 8-12 hours (Phases 1-3)
- Token savings: 30-50% per ticket
- Quality improvement: 20-30% fewer review cycles
- Break-even: After ~5 tickets

**Status:** Ready for implementation. Recommend starting with Phase 1 immediately.
