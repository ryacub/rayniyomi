# Download Queue Reducer Contract

This document is the source of truth for queue mutation invariants in the actor/reducer path.

## Commands
- `StartNow(downloadId)`: move (or create and move) target to front and request downloader start.
- `Reorder(orderedIds)`: reorder by known IDs only; unknown/stale IDs are ignored; non-mentioned IDs keep relative order at tail.
- `AddToStart(downloadIds)`: prepend only IDs not already queued; duplicates are ignored.
- `RemoveByItemIds(itemIds)`: remove matching queued items and preserve removal outcome under concurrency.

## Invariants
- Single writer: all queue mutations are serialized by `DownloadQueueActor`.
- Queue ID uniqueness is maintained by reducer semantics.
- Reorder/add/remove never resurrect removed IDs.
- Remove behavior keeps downloader transition parity:
  - when running: pause -> remove -> (start if queue remains, stop if empty)
  - when not running: remove only

## Failure Model
- Per-command failure completes that command exceptionally.
- Actor loop remains alive and continues processing later commands.
