# Coroutine Test Dispatchers

This document records the canonical pattern for dispatchers in screen models
and their coroutine tests. It applies to code introduced by PR #1052 and to
all new screen-model tests.

## The pattern

1. A class that owns its lifecycle scope takes a `CoroutineDispatcher`
   constructor parameter. Do not inject a `CoroutineScope`. The class builds
   its own scope from the dispatcher:

   ```kotlin
   class ExampleScreenModel(
       private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
   ) {
       private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
   }
   ```

2. Use `Dispatchers.IO` as the production default when the replaced path used
   `launchIO`.

3. Pass the dispatcher to each launch. Do not launch without it:

   ```kotlin
   scope.launch(ioDispatcher) { ... }
   ```

4. In tests, inject one `StandardTestDispatcher` that shares the `runTest`
   `TestCoroutineScheduler`. The helper `VirtualTime` in
   `app/src/test/java/eu/kanade/tachiyomi/test/VirtualTime.kt` owns this
   wiring: one shared `TestCoroutineScheduler`, `vt.main` is an
   `UnconfinedTestDispatcher`, and `vt.io` is a `StandardTestDispatcher`.
   Reuse `vt.io` where a production default of `Dispatchers.IO` needs a
   replacement.

5. Use `UnconfinedTestDispatcher` only for `Dispatchers.Main`, and only when
   the code under test needs immediate Main behavior.

6. Run each test with the shared scheduler, and drive work with scheduler
   operations:

   - `runTest(vt.scheduler) { ... }`
   - `advanceUntilIdle()` for finite work.
   - `advanceTimeBy(...)` for debounce, retry, and scheduled-delay boundaries.
   - `CompletableDeferred` or channels for ordering assertions.

7. Never use `Thread.sleep`, real-delay polling, or longer wall-clock
   timeouts. Virtual time keeps the suite deterministic.

## When you do not need dispatcher injection

Do not add dispatcher injection without a test-controlled asynchronous
boundary. If no test drives the asynchronous path through this class, an
injected dispatcher has no consumer. Add injection when you write the first
test that must control scheduling, not before.

## Focused example

The runnable example lives in
`app/src/test/java/eu/kanade/tachiyomi/test/DispatcherInjectionExampleTest.kt`.
The model follows rules 1 to 3:

```kotlin
private class FakeScreenModel(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    val started = CompletableDeferred<Unit>()

    init {
        scope.launch(dispatcher) {
            started.complete(Unit)
        }
    }

    fun cancel() {
        scope.cancel()
    }
}
```

A test on the injected, scheduler-owned dispatcher completes under a plain
drain (rules 4 and 6):

```kotlin
@Test
fun `a launch on the injected scheduler-owned dispatcher completes under advanceUntilIdle`() =
    runTest(vt.scheduler) {
        val model = FakeScreenModel(dispatcher = vt.io)

        advanceUntilIdle()

        assertTrue(model.started.isCompleted)
        model.started.await()
        model.cancel()
    }
```

A launch that escapes to a foreign dispatcher cannot be observed by the same
drain. The example proves this with a dispatcher the scheduler does not own.
It records the dispatch request, so the test shows both facts without any
real thread: the work left the shared scheduler, and the drain ran none of it.

```kotlin
@Test
fun `a launch that escapes to a foreign dispatcher is invisible to the shared scheduler`() =
    runTest(vt.scheduler) {
        val foreign = ForeignDispatcher()
        val model = FakeScreenModel(dispatcher = foreign)

        advanceUntilIdle()

        assertEquals(1, foreign.dispatchCount.get())
        assertFalse(model.started.isCompleted)
        model.cancel()
    }
```

This is why a hard-coded `Dispatchers.IO` inside a model breaks its tests:
the shared scheduler never sees the launched work, so `advanceUntilIdle()`
finishes with the work still pending. If a screen-model test fails this way,
check the launch site for a dispatcher that bypasses constructor injection.
