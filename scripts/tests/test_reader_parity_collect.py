from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from scripts.reader_parity_collect import main as collect_main


class ReaderParityCollectTest(unittest.TestCase):
    def test_collect_writes_candidate(self) -> None:
        baseline = Path("macrobenchmark/parity/reader_parity_baseline.json")
        with tempfile.TemporaryDirectory() as tmp:
            benchmark_observed = Path(tmp) / "benchmark_observed.json"
            benchmark_observed.write_text(
                json.dumps(
                    {
                        "metrics": {
                            "startup_to_reader_entry_ms_p95": 3000.0,
                            "open_chapter_ms_p95": 1800.0,
                            "webtoon_jank_percent_p95": 7.0,
                            "long_strip_memory_mb_p95": 420.0,
                            "recovery_pass_rate_percent": 100.0,
                            "variance_percent": 3.0,
                        },
                        "sampleSize": 100,
                        "scenarios": [
                            {"id": "startup_to_reader_entry", "status": "pass", "sampleCount": 25},
                        ],
                    },
                ),
                encoding="utf-8",
            )
            a11y_observed = Path(tmp) / "a11y_observed.json"
            a11y_observed.write_text(
                json.dumps(
                    {
                        "a11y": {
                            "automatedFocusOrderPassed": True,
                            "automatedRetryDiscoverabilityPassed": True,
                            "manualTalkBackChecklist": "macrobenchmark/parity/manual_talkback_checklist.md",
                            "manualTalkBackValidated": True,
                        },
                    },
                ),
                encoding="utf-8",
            )
            output = Path(tmp) / "candidate.json"
            import sys

            argv = sys.argv
            try:
                sys.argv = [
                    "reader_parity_collect.py",
                    "--baseline",
                    str(baseline),
                    "--benchmark-observed",
                    str(benchmark_observed),
                    "--a11y-observed",
                    str(a11y_observed),
                    "--output",
                    str(output),
                ]
                code = collect_main()
            finally:
                sys.argv = argv

            self.assertEqual(code, 0)
            payload = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(payload["schemaVersion"], 1)
            self.assertIn("candidateCommitSha", payload)
            self.assertEqual(payload["collection"]["source"], "reader_parity_collect.py")
            self.assertEqual(payload["sampleSize"], 100)

    def test_collect_fails_for_non_object_observed_payload(self) -> None:
        baseline = Path("macrobenchmark/parity/reader_parity_baseline.json")
        with tempfile.TemporaryDirectory() as tmp:
            benchmark_observed = Path(tmp) / "benchmark_observed.json"
            benchmark_observed.write_text("[]", encoding="utf-8")
            a11y_observed = Path(tmp) / "a11y_observed.json"
            a11y_observed.write_text("{}", encoding="utf-8")
            output = Path(tmp) / "candidate.json"

            import sys

            argv = sys.argv
            try:
                sys.argv = [
                    "reader_parity_collect.py",
                    "--baseline",
                    str(baseline),
                    "--benchmark-observed",
                    str(benchmark_observed),
                    "--a11y-observed",
                    str(a11y_observed),
                    "--output",
                    str(output),
                ]
                with self.assertRaises(ValueError):
                    collect_main()
            finally:
                sys.argv = argv


if __name__ == "__main__":
    unittest.main()
