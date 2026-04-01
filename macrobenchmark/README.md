# Baseline profiles

The baseline profile for this app is located at [`app/src/main/baseline-prof.txt`](../app/src/main/baseline-prof.txt).
It contains rules that enable AOT compilation of the critical user path taken during app launch.
For more information on baseline profiles, read [this document](https://developer.android.com/studio/profile/baselineprofiles).

> Note: The baseline profile needs to be re-generated for release builds that touch code which changes app startup.

To generate the baseline profile, select the `devBenchmark` build variant and run the
`BaselineProfileGenerator` benchmark test on an AOSP Android Emulator.
Then copy the resulting baseline profile from the emulator to [`app/src/main/baseline-prof.txt`](../app/src/main/baseline-prof.txt).

## Reader Parity Gate (R573 / #576)

Reader compose migration is CI-gated before deeper rewrite work.

Source of truth:
- Schema: [`macrobenchmark/parity/reader_parity_schema_v1.json`](parity/reader_parity_schema_v1.json)
- Baseline artifact: [`macrobenchmark/parity/reader_parity_baseline.json`](parity/reader_parity_baseline.json)
- Manual checklist: [`macrobenchmark/parity/manual_talkback_checklist.md`](parity/manual_talkback_checklist.md)
- Evaluator: [`scripts/reader_parity_gate.py`](../scripts/reader_parity_gate.py)

Key rules:
- UTC phase switch: warn through `2026-04-13T23:59:59Z`, enforce from `2026-04-14T00:00:00Z`.
- Relative thresholds: startup/open chapter `<= +10%`, webtoon jank `<= +15%`, long-strip memory `<= +10%`.
- Absolute checks: recovery `>= 99%`, variance `<= 15%`, fixed sample size must match baseline.
- Required scenarios must all be present and `pass`.
- Automated a11y checks must pass, and manual TalkBack validation must be explicitly signaled (`reader-parity-talkback-validated` label or workflow-dispatch input).

Baseline updates:
- Update baseline commit SHA and metrics in `reader_parity_baseline.json`.
- Require two maintainer approvals.
