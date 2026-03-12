# Evolve

See `.local/evolve.md` for the full framework documentation.

## Directory Layout

```
tools/evolve/
  README.md                              ← this file
  experiments/
    A-discover-feed/
      evolve.md                          ← strategy for Experiment A
      log.md                             ← generation history (added during runs)
```

## Benchmark Files

Benchmark test classes live in the app test source set (required for Gradle/JVM execution):

| Experiment | Benchmark file |
|------------|----------------|
| A — DiscoverFeedCoordinator throughput | `app/src/test/java/eu/kanade/domain/track/enrichment/evolve/FeedBench.kt` |

## Running Experiment A

```bash
./gradlew :app:testDebugUnitTest --tests "*FeedBench*" | grep "METRIC:"
```

Parse the `METRIC: <number>` line. Lower is better (ms per buildFeed call).
