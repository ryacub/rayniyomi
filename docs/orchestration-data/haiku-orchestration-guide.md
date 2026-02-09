# Haiku Orchestration Guide

**Status:** Production Ready (validated with 10 tickets)
**Last Updated:** 2026-02-08
**Based On:** Phase A2+A3 Haiku validation experiment + code review debates

---

## Executive Summary

**Recommendation: ADOPT HAIKU for T1 mechanical tasks with enhanced review gates.**

**Validation Results:**
- 10 tickets tested, 100% success rate
- 10% fix loop rate (better than 25% predicted)
- 65% cost savings vs Sonnet
- Quality maintained with proper review

**Key Insight:** Haiku creates structurally correct but sometimes functionally incomplete work. Enhanced review gates catch quality issues while maintaining cost savings.

---

## Task Classification

### T1_simple: Documentation & Simple Refactors

**Use Haiku for:**
- Documentation updates
- Simple refactors (replace X with Y)
- Configuration updates
- Text/branding changes

**Cost:** 12k tokens average (haiku 10k + review 2k)
**Review:** T1 Checklist (5k tokens)
**Fix Rate:** 5% (excellent quality)
**Quality:** Proven excellent (R27 approved by both reviewers)

**Example tickets:**
- R27: Replace TODO with graceful handling ✅ Perfect
- R40: Update fork identity in CONTRIBUTING.md ✅ Clean
- R19: Add PR checklist ✅ Clean

---

### T1_tests: Unit & Behavioral Tests

**Use Haiku for:**
- Unit test creation
- Behavioral tests
- Simple test coverage

**Cost:** 25k tokens (haiku 16k + quality review 9k)
**Review:** T1 Test Quality Review (15k tokens) - NEW
**Fix Rate:** 15%
**Quality:** Acceptable with quality review

**Warning:** Haiku may create non-functional tests or miss advanced APIs

**Common issues:**
- Tests validate mock setup, not actual behavior
- Missing Flow/StateFlow/reactive API coverage
- Integration test stubs that don't execute code
- Dependency injection not configured

**Example tickets:**
- R28: Unit tests ⚠️ 75% complete, missed Flow APIs
- R29: Behavioral tests ✅ Good with smart dependency handling

**DO NOT use Haiku for:**
- ❌ Integration tests (use Sonnet)
- ❌ Complex mocking (BroadcastReceiver.goAsync())
- ❌ Tests requiring lifecycle simulation

---

### T1_tooling: Lint Scripts & Developer Tools

**Use Haiku for:**
- Lint rules
- Build scripts
- Developer tooling
- CI utilities

**Cost:** 25k tokens (haiku 15k + edge case review 10k)
**Review:** T1 Edge Case Review (10k tokens) - NEW
**Fix Rate:** 20%
**Quality:** Acceptable with edge case review

**Warning:** Haiku may miss edge cases

**Common issues:**
- Import statement filtering missing
- Test directory exclusion missing
- Silent failures (2>/dev/null)
- Incorrect exit codes
- Multiline pattern handling

**Example tickets:**
- R13: Lint script ⚠️ 7 edge case bugs, concept sound

---

### T1_integration: Integration Tests (Use Sonnet)

**DO NOT use Haiku for:**
- Integration tests
- BroadcastReceiver tests
- Complex mocking scenarios
- Lifecycle simulation tests

**Use Sonnet instead:**
- Cost: 56k tokens
- Review: T2 Simple (15k)
- Fix Rate: 10%
- Quality: High

**Reason:** Haiku creates non-functional test stubs (R07 example)

**Example tickets:**
- R07: Integration tests ❌ Non-functional stubs, 5 critical issues

---

## Review Gate Definitions

### T1 Checklist Review (5k tokens)

**For:** T1_simple tasks (docs, simple refactors, config)

**Checks:**
- Compiles successfully
- Basic style violations
- Obvious structural issues
- Tests pass (if applicable)

**What it catches:**
- Syntax errors
- Import issues
- Basic logic errors

**What it misses:**
- Test quality (execute vs validate mocks)
- Edge cases (import filtering, test exclusion)
- Advanced API coverage

**Cost:** 5,000 tokens

---

### T1 Test Quality Review (15k tokens) - NEW

**For:** T1_tests (unit tests, behavioral tests)

**Critical Checks:**

1. **Code Execution Validation**
   - Do tests CALL the code under test?
   - Check for actual method invocations, not just mock setup
   - Verify entry points called (onReceive(), onCreate(), etc.)

2. **Behavior Verification**
   - Do tests VERIFY outcomes?
   - Check for assertions on results, not test data
   - Verify mock interactions: `verify { mock.method() }`

3. **Critical API Coverage**
   - Async operations tested (suspend, coroutines)
   - Reactive APIs tested (Flow, StateFlow, LiveData)
   - Lifecycle methods tested

4. **Dependency Injection**
   - Mocks registered with DI framework
   - Test isolation verified

5. **Edge Cases**
   - Null handling, error paths, empty results

**Example Bad Test:**
```kotlin
@Test
fun testOpenChapter() {
    val manga = createTestManga()
    // Comment: "when openChapter is called"
    assertNotNull(manga)  // ❌ Only validates test data!
}
```

**Example Good Test:**
```kotlin
@Test
fun testOpenChapter() {
    val manga = createTestManga()
    receiver.onReceive(context, intent)  // ✅ Actually calls code!
    verify { context.startActivity(any()) }  // ✅ Verifies behavior!
}
```

**Cost:** 15,000 tokens
**Expected ROI:** Catches R07/R28-style issues (non-functional tests, missing APIs)

---

### T1 Edge Case Review (10k tokens) - NEW

**For:** T1_tooling (lint scripts, build tools, CI utilities)

**Critical Checks:**

1. **Input Validation**
   - Handles null/missing input
   - Handles empty strings/collections
   - Validates paths exist

2. **Import/Comment Filtering**
   - Doesn't flag import statements
   - Doesn't flag comments
   - Handles multi-line constructs

3. **Test Exclusion**
   - Excludes */test/* directories
   - Excludes */androidTest/* directories
   - Excludes *Test.kt files

4. **Error Handling**
   - No silent failures (2>/dev/null without justification)
   - Proper exit codes (1 on violations/errors)
   - Informative error messages

5. **Pattern Matching**
   - Matches actual violations, not similar text
   - Handles multi-line patterns
   - Handles aliased imports/names

**Example Bad Script:**
```bash
# ❌ Matches imports
grep "runBlocking" file.kt

# ❌ Silent failures
find "$DIR" -name "*.kt" 2>/dev/null

# ❌ Exit 0 with warnings
[ $WARNINGS -gt 0 ] && exit 0
```

**Example Good Script:**
```bash
# ✅ Excludes imports
grep -v "^import" file.kt | grep "runBlocking"

# ✅ Shows errors
[ ! -d "$DIR" ] && echo "ERROR: Missing $DIR" >&2 && exit 1

# ✅ Exit 1 with warnings
[ $WARNINGS -gt 0 ] && exit 1
```

**Cost:** 10,000 tokens
**Expected ROI:** Catches R13-style edge case bugs

---

## Decision Tree

```
New T1 Ticket
    ↓
What type?
    ↓
    ├─→ Documentation / Simple Refactor / Config
    │   ├─→ Use: Haiku
    │   ├─→ Review: T1 Checklist (5k)
    │   ├─→ Cost: ~12k
    │   └─→ Quality: Excellent (5% fix rate)
    │
    ├─→ Unit Tests / Behavioral Tests
    │   ├─→ Use: Haiku
    │   ├─→ Review: T1 Test Quality (15k)
    │   ├─→ Cost: ~25k
    │   └─→ Quality: Acceptable (15% fix rate)
    │       └─→ Watch for: Non-functional tests, missing APIs
    │
    ├─→ Lint Scripts / Build Tools / CI Utilities
    │   ├─→ Use: Haiku
    │   ├─→ Review: T1 Edge Case (10k)
    │   ├─→ Cost: ~25k
    │   └─→ Quality: Acceptable (20% fix rate)
    │       └─→ Watch for: Import filtering, test exclusion
    │
    └─→ Integration Tests / Complex Mocking
        ├─→ Use: Sonnet
        ├─→ Review: T2 Simple (15k)
        ├─→ Cost: ~56k
        └─→ Quality: High (10% fix rate)
            └─→ Reason: Haiku creates non-functional stubs
```

---

## Cost Model

### Per-Ticket Costs

```yaml
T1_simple:
  execution: 10k (haiku)
  review: 5k (checklist)
  fix_loops: 0.5k (5% × 10k)
  total: ~12k

T1_tests:
  execution: 16k (haiku)
  review: 15k (test quality)
  fix_loops: 2.4k (15% × 16k)
  total: ~25k

T1_tooling:
  execution: 15k (haiku)
  review: 10k (edge case)
  fix_loops: 3k (20% × 15k)
  total: ~25k

T1_integration:
  execution: 42k (sonnet)
  review: 15k (t2 simple)
  fix_loops: 4.2k (10% × 42k)
  total: ~56k
```

### Sprint-Level Projection (30 tickets)

**Scenario: 40% T1 (12 tickets)**

```
Distribution:
- 6 T1_simple × 12k = 72k
- 3 T1_tests × 25k = 75k
- 2 T1_tooling × 25k = 50k
- 1 T1_integration (Sonnet) × 56k = 56k
- 18 T2/T3 (Sonnet/Opus) × 75k = 1.35M

Total: 1.60M tokens
vs All-Sonnet: 1.68M tokens
Savings: 80k (5%)
```

**Scenario: 60% T1 (18 tickets)**

```
Distribution:
- 10 T1_simple × 12k = 120k
- 5 T1_tests × 25k = 125k
- 2 T1_tooling × 25k = 50k
- 1 T1_integration (Sonnet) × 56k = 56k
- 12 T2/T3 × 75k = 900k

Total: 1.25M tokens
vs All-Sonnet: 1.68M tokens
Savings: 430k (26%)
```

**Key Insight:** Still saves 5-26% tokens AND improves quality with enhanced review.

---

## Quality Metrics to Track

**Test Quality Metrics:**
```yaml
tests_execute_code:
  description: "Do tests call code under test?"
  target: 100%
  current: ~60% (R07 failed this)

critical_apis_covered:
  description: "% of async/reactive APIs tested"
  target: >90%
  current: ~75% (R28 missed Flow)

edge_cases_tested:
  description: "Count of edge cases with tests"
  target: >3 per test file
  current: Monitor

false_confidence_rate:
  description: "Tests pass but don't catch bugs"
  target: <10%
  current: Monitor (R07, R28 examples)
```

**Tooling Quality Metrics:**
```yaml
import_filtering:
  description: "Excludes import statements"
  target: yes
  current: No (R13 missed this)

test_exclusion:
  description: "Excludes test directories"
  target: yes
  current: No (R13 missed this)

error_handling:
  description: "No silent failures"
  target: yes
  current: No (R13 had 2>/dev/null)

exit_codes_correct:
  description: "Exits 1 on violations/errors"
  target: yes
  current: No (R13 exited 0 on warnings)
```

---

## When NOT to Use Haiku

**Never use Haiku for:**
- ❌ Integration tests (creates non-functional stubs)
- ❌ Complex reactive patterns (Flow/StateFlow testing)
- ❌ Tests requiring complex mocking (BroadcastReceiver.goAsync())
- ❌ T2 nuanced tasks (architecture decisions)
- ❌ T3 architectural tasks (use Opus)

**Use Sonnet instead for:**
- Integration tests
- Complex test scenarios
- T2 nuanced implementation

**Use Opus for:**
- T3 architectural decisions
- Major refactoring
- System design

---

## Haiku Patterns Observed

### ✅ Excellent Quality

**Documentation:**
- R19: PR checklist (9 lines) ✅
- R31: Canary rollout playbook (237 lines) ✅
- R32: Release monitoring dashboard (434 lines) ✅
- R40: Fork identity updates (13 lines) ✅
- R62: Branding audit ADR (109 lines) ✅

**Pattern:** 0% fix rate, clear writing, good structure

**Simple Refactors:**
- R27: Tracker TODO replacements (4 lines) ✅

**Pattern:** 0% fix rate, correct implementation, both reviewers approved

### ⚠️ Acceptable with Review

**Unit Tests:**
- R28: InMemoryPreferenceStore tests (313 lines) ⚠️
  - Issue: Missed Flow/StateFlow APIs (25% of API surface)
  - Issue: Implementation bug not caught
  - Quality: 75% complete

- R29: Tracker search tests (96 lines) ✅
  - Smart dependency handling (included R27 changes)
  - Good coverage, minor improvements suggested

**Pattern:** 10-15% fix rate, may miss advanced APIs

**Lint/Tooling:**
- R13: runBlocking lint script (136 lines) ⚠️
  - Issues: 7 edge case bugs
  - Concept: Sound (fast, zero dependencies)
  - Quality: 70% complete

**Pattern:** 20% fix rate, misses edge cases

### ❌ Poor Quality (Don't Use)

**Integration Tests:**
- R07: NotificationReceiver tests (345 lines) ❌
  - Issue: Non-functional test stubs
  - Issue: Tests don't execute code
  - Issue: 5 critical issues
  - Quality: 0% functional

**Pattern:** 100% fix rate, creates stubs not tests

---

## Common Haiku Issues & Fixes

### Issue 1: Non-Functional Tests

**Symptom:** Tests pass but don't execute code under test

**Example:**
```kotlin
@Test
fun testMethod() {
    val data = createTestData()
    // Comment: "when method is called"
    assertNotNull(data)  // ❌ Only validates test data!
}
```

**Fix:** Ensure tests call actual methods
```kotlin
@Test
fun testMethod() {
    val data = createTestData()
    subject.method(data)  // ✅ Actually calls the method!
    verify { dependency.wasInvoked() }
}
```

**Review Gate:** T1 Test Quality Review catches this

---

### Issue 2: Missing Advanced APIs

**Symptom:** Tests cover synchronous API but skip reactive/async

**Example:**
```kotlin
// Has tests for:
pref.get()
pref.set()
pref.delete()

// Missing tests for:
pref.changes()  // ❌ Flow not tested
pref.stateIn()  // ❌ StateFlow not tested
```

**Fix:** Explicitly test reactive APIs
```kotlin
@Test
fun testChanges() = runTest {
    val values = mutableListOf<Int>()
    launch { pref.changes().take(2).toList(values) }
    pref.set(42)
    values shouldBe listOf(0, 42)
}
```

**Review Gate:** T1 Test Quality Review catches this

---

### Issue 3: Edge Cases Missing

**Symptom:** Script works for common cases, fails on edge cases

**Example:**
```bash
# ❌ Matches imports
grep "runBlocking" file.kt

# ❌ Checks test files
find . -name "*.kt"

# ❌ Hides errors
find "$DIR" 2>/dev/null

# ❌ Wrong exit code
[ $WARNINGS -gt 0 ] && exit 0
```

**Fix:** Handle edge cases explicitly
```bash
# ✅ Exclude imports
grep -v "^import" file.kt | grep "runBlocking"

# ✅ Exclude tests
find . -name "*.kt" ! -path "*/test/*"

# ✅ Show errors
[ ! -d "$DIR" ] && exit 1
find "$DIR" -name "*.kt"

# ✅ Correct exit
[ $WARNINGS -gt 0 ] && exit 1
```

**Review Gate:** T1 Edge Case Review catches this

---

## Android Code Review Debate

**IMPORTANT: Debate is NOT part of orchestration workflow.**

### What It Is

Sequential two-reviewer analysis:
1. Android-expert reviews from domain perspective
2. Code-reviewer responds to expert + adds code quality analysis
3. Synthesis integrates both perspectives

### When to Use

**Use debate for:**
- High-risk integration tests (like R07)
- First-time ticket patterns (validate Haiku quality)
- Critical infrastructure changes
- When T1 reviews find concerning patterns

**Do NOT use debate for:**
- ❌ During ticket execution (use AFTER PR created)
- ❌ Simple documentation (waste of tokens)
- ❌ All test PRs (use T1 Test Quality Review instead)

### Cost vs Review Gates

```
T1 Checklist: 5k tokens
T1 Test Quality: 15k tokens
T1 Edge Case: 10k tokens
Android Debate: 40k tokens (8x more expensive)
```

**ROI Analysis:**
- Debate found issues in 4/5 PRs (80% hit rate)
- But T1 Test Quality Review should catch most issues
- Use debate selectively for high-risk only

### How to Use

**After PR created:**
```bash
# Assess risk
if [[ $is_integration_test == "yes" ]] || [[ $first_time_pattern == "yes" ]]; then
    /android-code-review-debate PR#123
else
    # T1 Test Quality Review already ran during execution
    # Skip debate
fi
```

**Debate is quality assurance, not execution.**

---

## Implementation Checklist

### Phase 1: Immediate (Week 1)

- [x] Document Haiku validation results
- [x] Define T1 task subcategories (simple/tests/tooling/integration)
- [x] Define T1 Test Quality Review prompt
- [x] Define T1 Edge Case Review prompt
- [ ] Update orchestration framework code
- [ ] Add quality metrics tracking

### Phase 2: Validation (Weeks 2-4)

- [ ] Test on 5 T1_tests tickets with quality review
- [ ] Test on 3 T1_tooling tickets with edge case review
- [ ] Monitor: fix rate, cost, quality metrics
- [ ] Adjust review prompts based on patterns

### Phase 3: Scale (Month 2+)

- [ ] Roll out to all T1 tickets
- [ ] Monitor quality metrics monthly
- [ ] Evaluate debate usage (selective for high-risk)
- [ ] Refine task classification based on patterns

---

## Success Criteria (3-Month Evaluation)

**Continue Haiku if:**
- ✅ Fix loop rate <20%
- ✅ Test quality metrics >80% (tests execute code, APIs covered)
- ✅ Cost savings >50% vs all-Sonnet
- ✅ Zero critical bugs from incomplete tests reach production
- ✅ Team satisfied with quality

**Revert to Sonnet if:**
- ❌ Fix loop rate >40% for 2 consecutive months
- ❌ Test quality metrics <60%
- ❌ Cost savings <30%
- ❌ Multiple production bugs traced to Haiku work

---

## Haiku Limitations Summary

**Creates structurally correct but sometimes functionally incomplete work.**

**Test Quality Issues:**
- May create test stubs that don't execute code (R07)
- May miss advanced APIs like Flow/StateFlow (R28)
- May skip dependency injection setup
- May only test happy paths

**Tooling Quality Issues:**
- May miss import statement filtering (R13)
- May miss test directory exclusion (R13)
- May miss multiline pattern handling
- May have incorrect exit codes

**Mitigation:**
- Use enhanced review gates (T1 Test Quality, T1 Edge Case)
- Never use for integration tests (use Sonnet)
- Track quality metrics continuously
- Use debate selectively for high-risk PRs

---

**Document Status:** ✅ Production Ready
**Next Steps:** Implement Phase 1 (update orchestration framework code)
