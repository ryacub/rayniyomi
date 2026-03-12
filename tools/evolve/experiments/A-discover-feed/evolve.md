# Evolve: A — DiscoverFeedCoordinator.buildFeed Throughput

## Objective
Minimize ms per call to `buildFeed()` on the fixed benchmark dataset.

Run with:
```
./gradlew :app:testDebugUnitTest --tests "*FeedBench*"
```
Parse line: `METRIC: <number>`
**Current best:** 4.272 ms/call (gen3). Beat this. Lower is better.

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

- [x] Replace two-pass library iteration (seedGenreMap + topGenres separately) with a single combined pass — DONE in gen1 (−15.8%)
- [x] Replace String key `"${mediaType}:${stableKey}"` in groupBy with `Pair<EnrichmentMediaType, String>` — DONE in gen1
- [x] Precompute `normalizeGenre` for each library item once and store in seedGenreMap values — DONE in gen1
- [x] Replace `mergedGenres.intersect(topGenres).size` with `mergedGenres.count { topGenres.contains(it) }` — DONE in gen1
- [ ] Move `filterNot { it.recommendation.inLibrary }` before `groupBy` — already the case in baseline
- [x] Use `asSequence()` from filterNot through map to avoid intermediate List allocations in the dedup pipeline — DONE in gen2
- [ ] Replace `topGenres` from `.keys` (unordered Set) with a `HashSet<String>` explicitly — contains() is already O(1) but explicit type helps escape analysis
- [ ] Cache `seedCompositeScore` lookup inside the grouped candidates loop using local var instead of repeated map access
- [x] Replace `flatMap { it.recommendation.trackerSources }.toSet()` with a MutableSet accumulation to avoid intermediate list allocation — DONE in gen2
- [ ] Use `buildList` instead of `mutableListOf()` for deduped candidates to allow Kotlin to size-hint the list
- [x] HashSet for seedKeys + HashSet for mergedGenres + inline compositeScore accumulation — DONE in gen3
- [x] Pre-sized groupBy: replace `asSequence().filterNot().groupBy()` with a manual `LinkedHashMap(recommendations.size * 2)` loop — tried gen7, slower (stdlib groupBy is better JIT-optimized)
- [x] Collapse compSum/compCount and fromRecentSeed loops into single seedKeys traversal — tried in gen4, was slower (JIT/cache disruption)
- [x] Fuse all 4 `grouped` passes (trackers+seedKeys+rankScore+confidence) into one loop — tried gen8, slower (same loop fusion anti-pattern)
- [x] Replace `HashMap<String, Int>` (genreFreq) with `HashSet<String>` for topGenres — tried gen9, slower in controlled comparison despite one lucky cold reading

## Generation Log

| Gen | Summary of change | Metric | Kept? |
|-----|-------------------|--------|-------|
| 0   | baseline          | 6.432  | —     |
| 1   | single-pass library iter + Pair groupBy key + pre-norm genres + count{contains} instead of intersect | 5.415 | ✅    |
| 2   | asSequence() on recommendations pipeline + MutableSet accumulation for trackers | 4.653 | ✅    |
| 3   | HashSet+forEach for seedKeys; inline compSum/compCount loop; HashSet+forEach for mergedGenres | 4.272 | ✅    |
| 4   | collapse compSum/compCount + fromRecentSeed into single seedKeys forEach pass | 4.854 | ❌ reverted |
| 5   | remove asSequence + HashMap(size*2) instead of associateBy for seedCompositeScore | 4.926 | ❌ reverted |
| 6   | filterTo(ArrayList)+mapTo(HashSet) for recentSeeds instead of filter+map+toSet | 5.129 | ❌ reverted |
| 7   | pre-sized LinkedHashMap(size*2) inline groupBy loop instead of asSequence().groupBy() | 4.360 | ❌ reverted |
| 8   | fuse 4 grouped passes (trackers+seedKeys+rankScore+confidence) into one forEach | 4.735 | ❌ reverted |
| 9   | HashSet instead of HashMap<String,Int> for topGenres (use addAll vs freq counting) | 7.037† | ❌ reverted |

_† Controlled A/B in same thermal window. Gen3 measured 5.577 ms concurrently (vs stored 4.272 — thermal drift between sessions)._

## Learnings
- **Gen 1 (−15.8%):** Combining two library passes into one was the biggest win. Pre-normalizing genres at build time avoids redundant `normalizeGenre()` calls (which are `trim().lowercase()`) on every candidate × every seed. The Pair groupBy key and `count{contains}` replacements likely contributed smaller but meaningful savings. All four changes stacked well because they touch different parts of the hot path.
- **Gen 2 (−14.1%):** `asSequence()` avoids materializing the 320-item filtered list before groupBy. MutableSet accumulation for trackerSources avoids flatMap's intermediate list.
- **Gen 3 (−8.2%):** Replacing `toHashSet()` with explicit `HashSet(grouped.size * 2)` + `forEach { add }` for seedKeys, then inlining the compositeScore accumulation loop directly into the seedKeys.forEach, and using `HashSet<String>()` for mergedGenres with direct `forEach { add }`. Eliminates several intermediate collection creations.
- **Gen 4 (worse, −reverted):** Collapsing compSum/compCount + fromRecentSeed into one seedKeys pass was slower, likely because the JVM's loop optimizations (LICM, vectorization) work better on separate, simple loops. Also possible that fusing the conditions disrupts branch prediction patterns the JIT had optimized.
- **Gen 5 (worse, reverted):** Removing `asSequence()` was a clear regression. The lazy evaluation of filterNot → groupBy is materially beneficial for 400 items. The `HashMap(size*2)` for seedCompositeScore also didn't help — `associateBy` produces a LinkedHashMap which is fine for this workload.
- **Gen 6 (worse, reverted):** `filterTo(ArrayList)` + `mapTo(HashSet)` for recentSeeds was slower than the idiomatic `filter+map+toSet`. The functional chain is likely better JIT-compiled/inlined. recentSeeds creation is not the hot path anyway (50 snapshots only).
- **Gen 7 (worse, reverted):** Pre-sized `LinkedHashMap` with inline `getOrPut` loop was slower than `asSequence().groupBy()`. The stdlib groupBy is well JIT-compiled; our manual loop added `getOrPut` lambda overhead that outweighed rehash savings.
- **Gen 8 (worse, reverted):** Fusing all 4 grouped passes into one forEach was slower — confirms the loop fusion anti-pattern generalizes to `grouped` passes too, not just `seedKeys` passes. JVM prefers short single-purpose loops.
- **Gen 9 (worse, reverted):** `HashSet.addAll(normalized)` for topGenres was slower than `HashMap<String,Int>` freq counting in controlled A/B (Gen9: 7.037 vs Gen3: 5.577 ms same thermal window). Initial 3.842 ms reading was a cold-machine artifact.
- **⚠️ Benchmark variance:** This JVM benchmark is sensitive to CPU thermal state. Same code can measure 4.272 ms (cool) vs 5.577 ms (warm). Always compare A vs B in the same thermal window (back-to-back runs) not across sessions. Differences <0.3 ms are within noise.
- **Pattern:** Micro-optimizations to non-hot-path code (recentSeeds: 50 items) don't pay off. Focus on the groupBy and per-candidate inner loop (320 × 8 avg group size work). Loop fusion hurts JIT; separate passes help. Stdlib collection operations are often better JIT-compiled than manual equivalents.
