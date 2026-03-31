from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from scripts.reader_parity_extract_benchmark import main as extract_main


class ReaderParityExtractBenchmarkTest(unittest.TestCase):
    def test_extracts_last_marker_payload(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmpdir = Path(tmp)
            log_path = tmpdir / "benchmark.log"
            output_path = tmpdir / "observed.json"
            log_path.write_text(
                "noise\n"
                "RPARITY_OBSERVED_JSON:{\"metrics\":{\"startup_to_reader_entry_ms_p95\":1000},\"sampleSize\":100,\"scenarios\":[]}\n"
                "RPARITY_OBSERVED_JSON:{\"metrics\":{\"startup_to_reader_entry_ms_p95\":1100},\"sampleSize\":100,\"scenarios\":[]}\n",
                encoding="utf-8",
            )

            import sys

            argv = sys.argv
            try:
                sys.argv = [
                    "reader_parity_extract_benchmark.py",
                    "--log",
                    str(log_path),
                    "--output",
                    str(output_path),
                ]
                code = extract_main()
            finally:
                sys.argv = argv

            self.assertEqual(code, 0)
            payload = json.loads(output_path.read_text(encoding="utf-8"))
            self.assertEqual(payload["metrics"]["startup_to_reader_entry_ms_p95"], 1100)


if __name__ == "__main__":
    unittest.main()
