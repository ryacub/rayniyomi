#!/usr/bin/env python3
"""Generate a reader parity candidate report artifact."""

from __future__ import annotations

import argparse
import copy
import datetime as dt
import json
import os
import re
import subprocess
from pathlib import Path
from typing import Any

SHA_RE = re.compile(r"^[0-9a-f]{7,40}$")


def utc_now_iso() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def detect_commit_sha() -> str:
    env_sha = os.environ.get("GITHUB_SHA")
    if env_sha and SHA_RE.match(env_sha):
        return env_sha
    out = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if not SHA_RE.match(out):
        raise ValueError("Unable to determine candidate commit SHA")
    return out


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--baseline",
        default="macrobenchmark/parity/reader_parity_baseline.json",
        help="Baseline report JSON path",
    )
    parser.add_argument(
        "--output",
        default="macrobenchmark/parity/reader_parity_candidate.json",
        help="Output candidate report JSON path",
    )
    parser.add_argument(
        "--benchmark-observed",
        required=True,
        help="Observed benchmark metrics/scenarios JSON path",
    )
    parser.add_argument(
        "--a11y-observed",
        required=True,
        help="Observed a11y contract JSON path",
    )
    return parser.parse_args()


def expect_mapping(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ValueError(f"{path} must be an object")
    return value


def main() -> int:
    args = parse_args()
    baseline_path = Path(args.baseline)
    benchmark_observed_path = Path(args.benchmark_observed)
    a11y_observed_path = Path(args.a11y_observed)
    output_path = Path(args.output)

    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    benchmark_observed = expect_mapping(
        json.loads(benchmark_observed_path.read_text(encoding="utf-8")),
        "benchmark_observed",
    )
    a11y_observed = expect_mapping(
        json.loads(a11y_observed_path.read_text(encoding="utf-8")),
        "a11y_observed",
    )
    candidate = copy.deepcopy(baseline)

    observed_metrics = expect_mapping(benchmark_observed.get("metrics"), "benchmark_observed.metrics")
    observed_scenarios = benchmark_observed.get("scenarios")
    if not isinstance(observed_scenarios, list) or not observed_scenarios:
        raise ValueError("benchmark_observed.scenarios must be a non-empty array")
    observed_sample_size = benchmark_observed.get("sampleSize")
    if not isinstance(observed_sample_size, int) or observed_sample_size <= 0:
        raise ValueError("benchmark_observed.sampleSize must be a positive integer")

    observed_a11y = expect_mapping(a11y_observed.get("a11y"), "a11y_observed.a11y")

    candidate["generatedAtUtc"] = utc_now_iso()
    candidate["candidateCommitSha"] = detect_commit_sha()
    candidate["metrics"] = observed_metrics
    candidate["scenarios"] = observed_scenarios
    candidate["sampleSize"] = observed_sample_size
    candidate["a11y"] = observed_a11y
    candidate["collection"] = {
        "mode": "observed-artifact-merge",
        "source": "reader_parity_collect.py",
        "runId": os.environ.get("GITHUB_RUN_ID", "local"),
        "benchmarkObservedPath": str(benchmark_observed_path),
        "a11yObservedPath": str(a11y_observed_path),
    }

    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(candidate, indent=2) + "\n", encoding="utf-8")
    print(f"wrote: {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
