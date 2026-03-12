# Evolve: A — DiscoverFeedCoordinator.buildFeed Throughput

## Objective
Minimize ms per call to `buildFeed()` on the fixed benchmark dataset.

Run with:
```
./gradlew :app:testDebugUnitTest --tests "*FeedBench*"
```
Parse line: `METRIC: <number>`
**Current best:** 6.432 ms/call (baseline). Beat this. Lower is better.

## Target File
`app/src/main/java/eu/kanade/domain/track/enrichment/DiscoverFeedCoordinator.kt`

## Benchmark File (immutable — do not modify)
`app/src/test/java/eu/kanade/domain/track/enrichment/evolve/FeedBench.kt`

Dataset shape:
- 400 synthetic recommendations across 50 seeds (25 manga + 25 anime)
- 200 library items (100 manga + 100 anime) with genre lists
- 50 cache snapshots with composite scores
- stableKey space: 200 unique → ~50% dedup ratio in groupBy
- 20% of recs have inLibrary=true → filtered by filterNot early
- Output: top 50 feed items after ranking

## Hard Constraints
- Do not change `refresh()` or `observe()` public method signatures
- Do not add new Gradle dependencies
- Do not modify FeedBench.kt, EnrichmentCacheRepository, DiscoverRankingEngine, or model classes
- Behavior must be identical: same output for same inputs (no correctness regression)
- Code must compile: `./gradlew :app:compileDebugKotlin`

## Hypothesis Queue

- [ ] Replace two-pass library iteration (seedGenreMap + topGenres separately) with a single combined pass that builds both maps simultaneously — avoids iterating mangaLibrary and animeLibrary twice
- [ ] Move `filterNot { it.recommendation.inLibrary }` before `groupBy` — reduces map entries earlier (20% fewer groups)
- [ ] Replace String key `"${mediaType}:${stableKey}"` in groupBy with `Pair<EnrichmentMediaType, String>` — avoids String allocation per record
- [ ] Use `asSequence()` from filterNot through map to avoid intermediate List allocations in the dedup pipeline
- [ ] Precompute `normalizeGenre` for each library item once and store in seedGenreMap values (instead of re-normalizing inside the per-candidate `mergedGenres` computation)
- [ ] Replace `mergedGenres.intersect(topGenres).size` with `mergedGenres.count { topGenres.contains(it) }` — avoids creating a new Set for each candidate
- [ ] Replace `topGenres` from `.keys` (unordered Set) with a `HashSet<String>` explicitly — contains() is already O(1) but explicit type helps escape analysis
- [ ] Cache `seedCompositeScore` lookup inside the grouped candidates loop using local var instead of repeated map access
- [ ] Replace `flatMap { it.recommendation.trackerSources }.toSet()` with a MutableSet accumulation to avoid intermediate list allocation
- [ ] Use `buildList` instead of `mutableListOf()` for deduped candidates to allow Kotlin to size-hint the list

## Generation Log

| Gen | Summary of change | Metric | Kept? |
|-----|-------------------|--------|-------|
| 0   | baseline          | 6.432  | —     |

## Learnings
[Accumulates as generations run — add why things did or didn't work]
