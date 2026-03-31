#!/usr/bin/env python3
"""Extract observed reader parity JSON payload from benchmark stdout logs."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

MARKER = "RPARITY_OBSERVED_JSON:"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--log", required=True, help="Benchmark stdout log path")
    parser.add_argument("--output", required=True, help="Observed benchmark JSON output path")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    log_path = Path(args.log)
    output_path = Path(args.output)

    payload = None
    for line in log_path.read_text(encoding="utf-8", errors="replace").splitlines():
        marker_index = line.find(MARKER)
        if marker_index >= 0:
            payload = line[marker_index + len(MARKER) :].strip()

    if payload is None:
        raise ValueError(f"No '{MARKER}' line found in {log_path}")

    parsed = json.loads(payload)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(json.dumps(parsed, indent=2) + "\n", encoding="utf-8")
    print(f"wrote: {output_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
