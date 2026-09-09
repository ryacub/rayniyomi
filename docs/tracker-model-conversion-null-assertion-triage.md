# Tracker model conversion null assertion triage (R1040)

This file records the disposition of the 10 production `!!` assertions in
`domain/track/anime` and `domain/track/manga` that R1032 put in the baseline.
R1040 kept all 10 and fixed none. Each site lists the call path that was checked
and the reason it stays.

An assertion earns a fix only when a null produces a wrong outcome: wrong control
flow, a stuck state, or lost work. A null that no code path can produce, or a null
that an enclosing catch already turns into the intended result, does not earn a
change (see Earned Changes in `AGENTS.md`). R1047 fixed 6 download assertions
because their nulls escaped an uncaught `launchIO` and failed the whole queue.
Every tracker site here sits inside a per-tracker catch by design, so no null can
escape to break unrelated work.

## The mapper

`DbTrack.toDomainTrack(idRequired: Boolean = true): DomainTrack?` is the only
producer of the asserted values. Its body is:

```kotlin
val trackId = id ?: if (!idRequired) -1 else return null
```

The function returns null on one condition only: `id == null` and
`idRequired == true`. With `idRequired = false` a null id maps to -1, so the
function always returns a non-null track.

## Kept (10)

### Group 1: `toDomainTrack(idRequired = false)!!` (4)

- `AddAnimeTracks.kt` `insertTrack.await(track.toDomainTrack(idRequired = false)!!)` (line 91)
- `AddAnimeTracks.kt` `track.toDomainTrack(idRequired = false)!!,` (line 95)
- `AddMangaTracks.kt` `insertTrack.await(track.toDomainTrack(idRequired = false)!!)` (line 91)
- `AddMangaTracks.kt` `track.toDomainTrack(idRequired = false)!!,` (line 95)

Unreachable with null. The mapper cannot return null for `idRequired = false`, so
the `!!` cannot throw. The sites also run inside `bindEnhancedTrackers`'s
`try { ... } catch (e: Exception)` block, which logs and continues. No wrong
outcome is possible. Kept.

### Group 2: `service!!` (2)

- `RefreshAnimeTracks.kt` `service!!.animeService.refresh(...)` (line 33)
- `RefreshMangaTracks.kt` `service!!.mangaService.refresh(...)` (line 33)

Unreachable with null. The preceding
`.filter { (_, service) -> service?.isLoggedIn == true }` drops every pair whose
service is null, because `null?.isLoggedIn == true` is false. After the filter
`service` is always non-null. Kotlin cannot narrow the platform value across the
filter lambda, so the code re-asserts it. The site is also inside
`try { ... } catch (e: Throwable) { service to e }`. No wrong outcome is possible.
Kept.

### Group 3: `refresh(...).toDomainTrack(idRequired = true)!!` (4)

- `RefreshAnimeTracks.kt` `.refresh(track.toDbTrack()).toDomainTrack()!!` (line 33)
- `TrackEpisode.kt` `.refresh(track.toDbTrack()).toDomainTrack(idRequired = true)!!` (line 40)
- `RefreshMangaTracks.kt` `.refresh(track.toDbTrack()).toDomainTrack()!!` (line 33)
- `TrackChapter.kt` `.refresh(track.toDbTrack()).toDomainTrack(idRequired = true)!!` (line 41)

Unreachable with null. The mapper returns null here only when the refreshed DB
track's `id` is null. The input track comes from `getTracks.await(id)`, which
reads persisted rows from the local database, so its `id` is a non-null primary
key. `toDbTrack()` copies that `id`, and every tracker `refresh` returns the same
DB track object it received, mutated in place through `copyPersonalFrom`.
MyAnimeList returns `parseAnimeItem(status, track)`, which is `track.apply { ... }`,
the same object; its `add` path runs `updateItem(track)`, which also returns the
same object. So the refreshed track keeps its non-null `id` and the `!!` cannot
throw.

Each site is also contained. `RefreshAnimeTracks` and `RefreshMangaTracks` wrap
the line in `try { ... } catch (e: Throwable) { service to e }` inside a
`supervisorScope`, so a failure reports one tracker and does not cancel the others.
`TrackEpisode` and `TrackChapter` wrap the line in `runCatching { ... }`, whose
throwable is logged. If a future tracker returned an id-less track, a `?: throw`
would produce the same failed-update result the catch already gives. No user- or
test-observable change is earned. Kept.

## Baseline

No assertion was removed, so `scripts/null_assertion_baseline.txt` is unchanged
and `./gradlew checkNullAssertions` reports no stale or new rows.
