# Baseline profiles

The baseline profile for this app is located at [`app/src/main/baseline-prof.txt`](../app/src/main/baseline-prof.txt).
It contains rules that enable AOT compilation of the critical user path taken during app launch.
For more information on baseline profiles, read [this document](https://developer.android.com/studio/profile/baselineprofiles).

> Note: The baseline profile needs to be re-generated for release builds that touch code which changes app startup.

To generate the baseline profile, select the `devBenchmark` build variant and run the
`BaselineProfileGenerator` benchmark test on an AOSP Android Emulator.
Then copy the resulting baseline profile from the emulator to [`app/src/main/baseline-prof.txt`](../app/src/main/baseline-prof.txt).

## Reader Parity Gate (R573 / #576)

Reader compose migration is guarded by a parity gate before deeper engine rewrite work.

### Source-of-truth artifacts

- Schema: [`macrobenchmark/parity/reader_parity_schema_v1.json`](parity/reader_parity_schema_v1.json)
- Baseline: [`macrobenchmark/parity/reader_parity_baseline.json`](parity/reader_parity_baseline.json)
- Manual a11y checklist: [`macrobenchmark/parity/manual_talkback_checklist.md`](parity/manual_talkback_checklist.md)
- Evaluator: [`scripts/reader_parity_gate.py`](../scripts/reader_parity_gate.py)

### Metric boundaries

Metrics are interpreted exactly as follows in the gate report:

- `startup_to_reader_entry_ms_p95`: p95 cold startup to first stable app frame (milliseconds)
- `open_chapter_ms_p95`: p95 chapter-open interaction latency proxy (milliseconds)
- `webtoon_jank_percent_p95`: p95 jank percentage for webtoon-scroll proxy
- `long_strip_memory_mb_p95`: p95 memory footprint proxy during long-strip scenario (MB)
- `recovery_pass_rate_percent`: aggregate pass rate across recovery scenarios (must be >= 99)
- `variance_percent`: run-to-run variance guardrail metric (must be <= 15)

### Threshold policy

Dual-threshold policy applies to performance regressions:

- Relative regression limit and absolute cap must both pass.
- Relative limits:
  - startup/open chapter <= +10%
  - webtoon jank <= +15%
  - long-strip memory <= +10%
- Recovery floor: `recovery_pass_rate_percent >= 99`
- Fixed sample size: `sampleSize` must equal baseline `sampleSize` for the pinned profile
- Variance guardrail: `variance_percent <= 15`

### Enforcement phases (UTC)

- Warn-only until `2026-04-13T23:59:59Z`
- Blocking fail from `2026-04-14T00:00:00Z`

The evaluator computes phase using UTC timestamps and emits `PASS`, `WARN`, `FAIL`, or `PASS_BYPASS`.

### Baseline governance

- Baseline updates require explicit commit SHA updates in `reader_parity_baseline.json`.
- Baseline updates should only be applied for approved perf-shifting changes.
- Baseline update review requires two maintainers.
- Manual TalkBack checklist must be completed and explicitly marked as validated (`reader-parity-talkback-validated` label or workflow-dispatch input) for baseline refreshes.

### Required scenarios

Candidate reports must include pass/fail state for all required scenarios:

- startup to reader entry
- open chapter latency
- webtoon jank scroll
- long-strip memory
- prefetch boundary transition
- process death recovery
- background/foreground resume
- offline retry after connectivity restore
- long images with mixed dimensions

Current implementation note: CI currently collects these through smoke proxies and shell-observed metrics; if proxy data is insufficient (for example sample-size shortfall), the evaluator emits regressions and transitions to `WARN`/`FAIL` per phase policy.
