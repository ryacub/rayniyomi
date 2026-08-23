# R854: Cover palette comparison evidence record

Ticket R854 (issue #876), risk tier T2. Question: should the pixel-average
sampler in `CoverThemePaletteService.extractDominantColor` be replaced with
AndroidX Palette?

Verdict: **keep the current sampler.** No production code changed.

## Method

Instrumented harness `CoverPaletteComparisonTest` ran on emulator-5554
(API 36, arm64). For each corpus cover it decoded a software bitmap at
production size (long edge 128 px) and computed two seeds:

- Seed A (`sampler`): production grid-average of opaque pixels.
- Seed B (`palette`): `Palette.from(bitmap).maximumColorCount(16).generate()`,
  dominant swatch, else highest-population swatch.

Each seed ran through real production `CoverThemeColorUtils.buildTokens`
for both themes. Metrics per cover and algorithm:

- `chroma` = max(RGB) - min(RGB); muddy = chroma < 24.
- `pre_enforcement_contrast`: WCAG contrast of the raw seed against the
  derived primary container. Container colors are pure blends of the seed;
  token enforcement touches only on-colors, so this is pre-enforcement
  contrast by construction.

The manifest with source URLs and sha256 hashes is committed at
`app/src/androidTest/assets/cover_corpus_manifest.txt`. The harness verified
every file hash before measuring and hard-failed on mismatch. Raw CSV:
`cover-palette-comparison-r854.csv`.

## Decision rule

Declared before the first full run. Palette wins iff ALL hold:

1. Muddy rate on the `flat` subset differs by at least 15 percentage points
   in Palette's favor (n >= 20 flat covers; smaller margins are a tie).
2. Mean pre-enforcement contrast of Palette seeds is within 10 percent of
   the sampler's (no large readability regression).
3. Mean chroma of Palette seeds is not lower than the sampler's.

Tie or mixed result keeps the current sampler.

## Results

50 covers (25 anime, 25 light novels). No cover excluded; both algorithms
produced a seed for every cover. A second full run produced a byte-identical
CSV, so the measurements are deterministic.

| Condition | Sampler | Palette | Verdict |
|---|---|---|---|
| 1. Flat muddy rate | 48.5% (16/33) | 51.5% (17/33) | +3.0 pp — FAIL |
| 2a. Mean pre-contrast, light | 3.45 | 6.04 | Palette higher — pass |
| 2b. Mean pre-contrast, dark | 1.74 | 2.08 | Palette higher — pass |
| 3. Mean chroma | 32.16 | 34.88 | pass |

Condition 1 fails: the flat-subset muddy rates differ by 3.0 percentage
points, far below the 15-point adoption threshold. Under the declared rule
this is a tie, so the current sampler stays.

Note on condition 2: read strictly as "within 10 percent either way", the
light-theme value (+75 percent) would fail; read as written ("no large
readability regression"), higher contrast passes. The outcome is unaffected
because condition 1 already decides against adoption.

## Deviations from plan

- Corpus classes: the plan required at least 5 `alpha_heavy` covers
  (transparent regions). None could be sourced. Kitsu PNG originals
  (22 checked), MangaDex original PNGs (~60 scanned) are fully opaque;
  AniList GraphQL is Cloudflare-blocked from this network, Jikan was in a
  504 outage, and Wikimedia rate-limits it. Mainstream CDNs flatten alpha,
  which mirrors what production Coil decodes. The decision rule never reads
  the alpha subset, so this cannot change the outcome.
- Covers came from the Kitsu CDN (`media.kitsu.app`) rather than
  MangaDex/AniList, because those two were unreachable from this network.
  All URLs and sha256 hashes are recorded in the committed manifest.

## Artifacts

- `app/src/androidTest/assets/cover_corpus_manifest.txt` — corpus sources
  and sha256 hashes (committed).
- `docs/cover-palette-comparison-r854.csv` — raw CSV from run 1; run 2 was
  byte-identical (committed).
- The instrumented harness was a one-off measurement tool. It was removed
  after the keep verdict per Phase 2B and was never committed. This doc plus
  the committed manifest and CSV are the reproducibility record.
