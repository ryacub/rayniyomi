# Cancellation catch baseline

The pull request workflow runs `./gradlew checkCancellationCatches`.

In a coroutine, `catch (e: Throwable)` and `catch (e: Exception)` also catch
`CancellationException`. The cancelled job then continues after the catch.
It can write an error state or show an error to a user who cancelled.

The task scans production Kotlin sources in every module except
`cloudstream-runtime-host`, which mirrors upstream signatures. It excludes
test sources and ignores comments and string literals. The source roots are
listed in the root `build.gradle.kts`. Add a new module's main source root
there.

## What the check accepts

A `catch` of `Throwable` or `Exception` passes when it rethrows cancellation
in one of these forms:

```kotlin
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    logcat(LogPriority.ERROR, e)
}
```

```kotlin
} catch (e: Throwable) {
    if (e is CancellationException) throw e
    logcat(LogPriority.ERROR, e)
}
```

Outside a coroutine, catch the narrowest exception type that the code can
throw, such as `IOException`.

`RuntimeException` and `IllegalStateException` also catch
`CancellationException`. The check does not inspect them.

## Baseline

The baseline is in
[`scripts/cancellation_catch_baseline.txt`](../scripts/cancellation_catch_baseline.txt).
Each entry contains the repository path, a tab, and the trimmed source line of
an existing catch. The source line keeps the entry stable when earlier lines
move.

When a ticket fixes a baseline site or removes it, remove its entry. When a
ticket adds a catch, make it pass the check instead of adding it to the
baseline. The task reports stale entries and new catches.

Most baseline sites cannot receive a cancellation, or they stop at the next
suspension point. The triage of the `Throwable` sites is on issue #1496.

Run `./gradlew checkCancellationCatches` before you commit the change.
