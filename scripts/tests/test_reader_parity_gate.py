from __future__ import annotations

import copy
import json
import tempfile
import unittest
from pathlib import Path

from scripts.reader_parity_gate import main as gate_main


class ReaderParityGateTest(unittest.TestCase):
    def setUp(self) -> None:
        baseline_path = Path("macrobenchmark/parity/reader_parity_baseline.json")
        self.baseline = json.loads(baseline_path.read_text(encoding="utf-8"))

    def _write(self, data: dict, path: Path) -> None:
        path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")

    def _run_gate(self, baseline: dict, candidate: dict, now_utc: str) -> tuple[int, dict]:
        with tempfile.TemporaryDirectory() as tmp:
            tmpdir = Path(tmp)
            baseline_path = tmpdir / "baseline.json"
            candidate_path = tmpdir / "candidate.json"
            output_path = tmpdir / "result.json"
            self._write(baseline, baseline_path)
            self._write(candidate, candidate_path)

            import sys

            argv = sys.argv
            try:
                sys.argv = [
                    "reader_parity_gate.py",
                    "--baseline",
                    str(baseline_path),
                    "--candidate",
                    str(candidate_path),
                    "--output",
                    str(output_path),
                    "--now-utc",
                    now_utc,
                ]
                code = gate_main()
            finally:
                sys.argv = argv

            result = json.loads(output_path.read_text(encoding="utf-8"))
            return code, result

    def test_warn_phase_regression_is_non_blocking(self) -> None:
        candidate = copy.deepcopy(self.baseline)
        candidate["metrics"]["startup_to_reader_entry_ms_p95"] = 5000.0

        code, result = self._run_gate(
            self.baseline,
            candidate,
            "2026-04-01T00:00:00Z",
        )

        self.assertEqual(code, 0)
        self.assertEqual(result["phase"], "warn")
        self.assertEqual(result["status"], "WARN")

    def test_enforce_phase_regression_blocks(self) -> None:
        candidate = copy.deepcopy(self.baseline)
        candidate["metrics"]["open_chapter_ms_p95"] = 2500.0

        code, result = self._run_gate(
            self.baseline,
            candidate,
            "2026-04-20T00:00:00Z",
        )

        self.assertEqual(code, 1)
        self.assertEqual(result["phase"], "enforce")
        self.assertEqual(result["status"], "FAIL")

    def test_missing_metric_is_fail_closed(self) -> None:
        candidate = copy.deepcopy(self.baseline)
        del candidate["metrics"]["variance_percent"]

        code, result = self._run_gate(
            self.baseline,
            candidate,
            "2026-04-20T00:00:00Z",
        )

        self.assertEqual(code, 1)
        self.assertEqual(result["status"], "FAIL")
        self.assertTrue(any("variance_percent" in err for err in result["errors"]))

    def test_sample_size_mismatch_fails_in_enforce_phase(self) -> None:
        candidate = copy.deepcopy(self.baseline)
        candidate["sampleSize"] = int(self.baseline["sampleSize"]) + 1

        code, result = self._run_gate(
            self.baseline,
            candidate,
            "2026-04-20T00:00:00Z",
        )

        self.assertEqual(code, 1)
        self.assertEqual(result["status"], "FAIL")
        self.assertTrue(any("sampleSize does not match fixed baseline sample size" in r for r in result["regressions"]))

    def test_validation_error_does_not_downgrade_in_warn_phase(self) -> None:
        candidate = copy.deepcopy(self.baseline)
        candidate["metrics"] = []

        code, result = self._run_gate(
            self.baseline,
            candidate,
            "2026-04-01T00:00:00Z",
        )

        self.assertEqual(code, 1)
        self.assertEqual(result["phase"], "warn")
        self.assertEqual(result["status"], "FAIL")
        self.assertTrue(any("metrics must be an object" in err for err in result["errors"]))

    def test_manual_talkback_flag_must_be_boolean(self) -> None:
        candidate = copy.deepcopy(self.baseline)
        candidate["a11y"]["manualTalkBackValidated"] = "true"

        code, result = self._run_gate(
            self.baseline,
            candidate,
            "2026-04-20T00:00:00Z",
        )

        self.assertEqual(code, 1)
        self.assertEqual(result["status"], "FAIL")
        self.assertTrue(any("manualTalkBackValidated must be boolean" in err for err in result["errors"]))


if __name__ == "__main__":
    unittest.main()
