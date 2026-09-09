# Anime download null assertion triage (R1047)

This file records the disposition of the 18 production `!!` assertions in
`data/download/anime` that R1032 put in the baseline. R1047 fixed 6 reachable
sites and kept 12. Each kept site lists the call path that was checked and the
reason it stays.

`UniFile` is a Java library with no nullability annotations, so Kotlin sees its
methods as platform types and `!!` compiles. `createFile` and `createDirectory`
return null on real I/O failure (permission denied, storage detached, volume
full, or a provider that rejects the name). `getName()` re-queries the content
provider on every call. `getFilePath()` returns null for any DocumentsProvider
authority that is not `com.android.externalstorage.documents` and is not under an
external cache directory.

An assertion earns a fix only when a null produces a wrong outcome: wrong control
flow, a stuck state, or lost work. An assertion whose null is already turned into
the correct user-visible error stays, because a `?: throw` there changes nothing
that a user or a test can observe (see Earned Changes in `AGENTS.md`).

## Fixed (6)

| Site | Cause of null | Wrong outcome before the fix | Fix |
|---|---|---|---|
| `AnimeDownloadCache.kt` `dir.name!!` (cache scan) | A source directory is deleted between the filter read and the `!!` read; each `getName()` is a separate provider query. | The NPE escapes `renewCache`'s uncaught `launchIO`, so the cache keeps stale contents and the "indexing" spinner stays on for up to one hour. | Read each directory name once via `namedChildDirectories`. |
| `AnimeDownloadCache.kt` `.map { it.name!! }` | Same, a third `getName()` on the same collection. | Same as above. | Same helper. |
| `AnimeDownloadCache.kt` `.associate { it.name!! ... }` | An anime directory is deleted mid-scan. | Same, inside `async` awaited by the same uncaught block. | Same helper. |
| `AnimeDownloader.kt` `createDirectory(...)!!` (line 503) | SAF write fails when the temp directory is created. | The `!!` is outside the episode `try`, so the NPE escapes to the outer catch, which calls `stop()`. One unwritable directory marks the whole queue as failed. | Move the creation inside the `try` and `?: throw IOException(...)`. The one episode fails through `forItem`; the queue keeps running. |
| `AnimeDownloader.kt` `tmpDir.filePath!!` (clipboard) | `getFilePath()` returns null for a non-external-storage authority (USB/OTG, SD card, cloud provider). | The NPE fires before `startActivity`, so a clipboard convenience failure stops the whole external download. | Read `filePath` once, guard the clipboard copy with `?.let`. |
| `AnimeDownloader.kt` `tmpDir.filePath!!` (ADM) | Same null path from the same read. | The NPE fires before the temp files are deleted, so it also leaves an orphan `_tmp` directory that the cache skips forever. | Reuse the single `filePath` read; `?: throw IOException(...)` because ADM needs a real local path. |

## Kept (12)

### `AnimeDownloadProvider.kt` (3)
- `return downloadsDir!!` (line 41)
- `.createDirectory(getSourceDirName(source))!!` (line 42)
- `.createDirectory(getAnimeDirName(animeTitle))!!` (line 43)

Reachable on SAF I/O failure or no configured storage. No wrong outcome: lines
39-50 wrap all three in `try { ... } catch (e: Throwable)`, which rethrows
`Exception(invalid_location)`. `downloadEpisode`'s catch at line 557 then reports
the item through `forItem` with the correct message. A `?: throw` gives a
byte-identical result. Kept.

### `AnimeDownloader.kt` (2)
- `val video = download.video!!` (line 584, `getOrDownloadVideoFile`)
- `downloadVideoExternal(download.video!!, ...)` (line 625)

Unreachable with null. `getOrDownloadVideoFile` has one caller (line 519). The
block above it (lines 506-515) assigns `download.video` or throws
`video_list_empty_error`. `download.video =` appears once in the subsystem (line
514), so nothing sets it back to null. Kept.

- `val file = tmpDir.createFile(...)!!` (line 679)

Reachable on SAF I/O failure. No observable wrong outcome: the line is inside
`downloadVideoExternal`'s `try`, whose catch deletes the temp file and reports
the item as `GENERIC`. A `?: throw` only changes the error code string from
`NullPointerException` to `IOException`. Kept.

### `AnimeVideoDownloader.kt` (4)
- `val videoFile = tmpDir.createFile(...)!!` (line 69)
- `val video = download.video!!` (lines 114, 149, 213)

The `createFile` site is reachable, but the retry classifier at line 86 needs an
`IOException` with an `EACCES`/`EPERM` message to fast-fail; UniFile returns a
bare null and swallows the errno, so no achievable change beyond the error code
string. Recorded as a follow-up idea: teach `isPermissionFailure` to recognise a
null-return SAF failure, or probe `parent.canWrite()` first. The three
`download.video!!` sites are private methods reached only through `download()`,
which runs after `download.video` is set. Kept.

### `model/AnimeDownloadPart.kt` (2)
- `_file = placingDir.createFile(...)!!` (line 46)
- `return _file!!` (line 48)

Unreachable: the class has no constructor call anywhere in the repository (grep
finds only the declaration and the two baseline rows). The multi-thread
downloader uses the `download/anime/multithread` types instead. Deleting the dead
class is a separate change from assertion triage, so it stays out of scope here.
Kept.
