#!/usr/bin/env python3
"""Evaluate reader parity candidate metrics against baseline and emit gate result."""

from __future__ import annotations

import argparse
import datetime as dt
import json
import re
from pathlib import Path
from typing import Any

EXPECTED_SCHEMA_VERSION = 1
UTC_CUTOFF = "2026-04-14T00:00:00Z"
SHA_RE = re.compile(r"^[0-9a-f]{7,40}$")
FOLLOWUP_RE = re.compile(r"^(#\d+|https://github\.com/[^/]+/[^/]+/issues/\d+)$")

REQUIRED_METRICS = {
    "startup_to_reader_entry_ms_p95": {"rel_max": 10.0, "abs_max": 8000.0},
    "open_chapter_ms_p95": {"rel_max": 10.0, "abs_max": 5000.0},
    "webtoon_jank_percent_p95": {"rel_max": 15.0, "abs_max": 25.0},
    "long_strip_memory_mb_p95": {"rel_max": 10.0, "abs_max": 1024.0},
}

REQUIRED_SCENARIOS = {
    "startup_to_reader_entry",
    "open_chapter_latency",
    "webtoon_jank_scroll",
    "long_strip_memory",
    "prefetch_boundary_transition",
    "process_death_recovery",
    "background_foreground_resume",
    "offline_retry_after_restore",
    "long_images_mixed_dimensions",
}

def parse_utc(value: str) -> dt.datetime:
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    parsed = dt.datetime.fromisoformat(value)
    if parsed.tzinfo is None:
        raise ValueError("timestamp must include timezone")
    return parsed.astimezone(dt.timezone.utc)


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def validate_report(report: dict[str, Any], name: str) -> list[str]:
    errors: list[str] = []
    required_top = {
        "schemaVersion",
        "generatedAtUtc",
        "baselineCommitSha",
        "candidateCommitSha",
        "deviceProfile",
        "sampleSize",
        "metrics",
        "scenarios",
        "a11y",
    }
    for key in sorted(required_top):
        if key not in report:
            errors.append(f"{name}: missing required field '{key}'")

    if report.get("schemaVersion") != EXPECTED_SCHEMA_VERSION:
        errors.append(f"{name}: schemaVersion must be {EXPECTED_SCHEMA_VERSION}")

    for sha_field in ("baselineCommitSha", "candidateCommitSha"):
        sha = report.get(sha_field)
        if not isinstance(sha, str) or not SHA_RE.match(sha):
            errors.append(f"{name}: {sha_field} must be a git SHA")

    try:
        parse_utc(str(report.get("generatedAtUtc", "")))
    except Exception as exc:  # noqa: BLE001
        errors.append(f"{name}: generatedAtUtc invalid ({exc})")

    device = report.get("deviceProfile") or {}
    for key in ("apiLevel", "profile", "arch", "target"):
        if key not in device:
            errors.append(f"{name}: deviceProfile.{key} is required")

    metrics = report.get("metrics")
    if not isinstance(metrics, dict):
        errors.append(f"{name}: metrics must be an object")
        metrics = {}
    for metric in list(REQUIRED_METRICS.keys()) + ["recovery_pass_rate_percent", "variance_percent"]:
        value = metrics.get(metric)
        if not isinstance(value, (int, float)):
            errors.append(f"{name}: metrics.{metric} must be numeric")

    sample_size = report.get("sampleSize")
    if not isinstance(sample_size, int) or sample_size <= 0:
        errors.append(f"{name}: sampleSize must be a positive integer")

    scenarios = report.get("scenarios")
    if not isinstance(scenarios, list):
        errors.append(f"{name}: scenarios must be a list")

    a11y = report.get("a11y")
    if not isinstance(a11y, dict):
        errors.append(f"{name}: a11y must be an object")
        a11y = {}
    for key in (
        "automatedFocusOrderPassed",
        "automatedRetryDiscoverabilityPassed",
        "manualTalkBackChecklist",
        "manualTalkBackValidated",
    ):
        if key not in a11y:
            errors.append(f"{name}: a11y.{key} is required")
    for bool_key in (
        "automatedFocusOrderPassed",
        "automatedRetryDiscoverabilityPassed",
        "manualTalkBackValidated",
    ):
        value = a11y.get(bool_key)
        if not isinstance(value, bool):
            errors.append(f"{name}: a11y.{bool_key} must be boolean")

    return errors


def evaluate(baseline: dict[str, Any], candidate: dict[str, Any]) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    regressions: list[str] = []

    if baseline.get("deviceProfile") != candidate.get("deviceProfile"):
        errors.append("deviceProfile mismatch between baseline and candidate")

    bmetrics = baseline["metrics"]
    cmetrics = candidate["metrics"]

    for metric, rule in REQUIRED_METRICS.items():
        base = float(bmetrics[metric])
        cand = float(cmetrics[metric])
        if base <= 0:
            errors.append(f"baseline metric '{metric}' must be > 0")
            continue
        delta = ((cand - base) / base) * 100.0
        if delta > rule["rel_max"]:
            regressions.append(
                f"{metric} relative delta {delta:.2f}% exceeds {rule['rel_max']:.2f}%",
            )
        if cand > rule["abs_max"]:
            regressions.append(
                f"{metric} absolute value {cand:.2f} exceeds {rule['abs_max']:.2f}",
            )

    if float(cmetrics["recovery_pass_rate_percent"]) < 99.0:
        regressions.append("recovery_pass_rate_percent below 99.0")
    baseline_sample_size = int(baseline["sampleSize"])
    candidate_sample_size = int(candidate["sampleSize"])
    if candidate_sample_size != baseline_sample_size:
        regressions.append(
            "sampleSize does not match fixed baseline sample size "
            f"({candidate_sample_size} != {baseline_sample_size})",
        )
    if float(cmetrics["variance_percent"]) > 15.0:
        regressions.append("variance_percent exceeds flake guardrail (15.0)")

    scenario_map = {item.get("id"): item for item in candidate["scenarios"] if isinstance(item, dict)}
    missing = REQUIRED_SCENARIOS - set(scenario_map)
    if missing:
        errors.append(f"missing required scenarios: {', '.join(sorted(missing))}")

    for scenario_id in sorted(REQUIRED_SCENARIOS & set(scenario_map)):
        status = scenario_map[scenario_id].get("status")
        if status != "pass":
            regressions.append(f"scenario '{scenario_id}' status is '{status}'")

    a11y = candidate["a11y"]
    if not a11y.get("automatedFocusOrderPassed"):
        regressions.append("a11y automated focus-order check failed")
    if not a11y.get("automatedRetryDiscoverabilityPassed"):
        regressions.append("a11y automated retry discoverability check failed")
    if not a11y.get("manualTalkBackValidated"):
        regressions.append("manual TalkBack checklist not validated")

    checklist = a11y.get("manualTalkBackChecklist")
    if not isinstance(checklist, str) or not checklist.strip():
        errors.append("a11y.manualTalkBackChecklist must be a non-empty path")

    return regressions, errors


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--candidate", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--now-utc", help="Override current UTC timestamp in ISO-8601 format")
    parser.add_argument("--enforce-after-utc", default=UTC_CUTOFF)
    parser.add_argument("--allow-bypass", action="store_true")
    parser.add_argument("--bypass-reason")
    parser.add_argument("--followup-issue")
    return parser.parse_args()


def main() -> int:
    args = parse_args()

    baseline = load_json(Path(args.baseline))
    candidate = load_json(Path(args.candidate))

    validation_errors = validate_report(baseline, "baseline") + validate_report(candidate, "candidate")
    errors: list[str] = list(validation_errors)
    regressions: list[str] = []
    if not validation_errors:
        regressions, eval_errors = evaluate(baseline, candidate)
        errors.extend(eval_errors)

    now = parse_utc(args.now_utc) if args.now_utc else dt.datetime.now(dt.timezone.utc)
    cutoff = parse_utc(args.enforce_after_utc)
    phase = "warn" if now < cutoff else "enforce"

    gate_state = "PASS" if not regressions and not errors else "FAIL"
    status = gate_state
    bypass_applied = False

    # Validation failures remain hard failures in all phases (fail-closed).
    if gate_state == "FAIL" and phase == "warn" and not validation_errors:
        status = "WARN"

    if gate_state == "FAIL" and phase == "enforce" and args.allow_bypass:
        if not args.bypass_reason:
            errors.append("bypass requested without --bypass-reason")
        if not args.followup_issue or not FOLLOWUP_RE.match(args.followup_issue):
            errors.append("bypass requested without valid --followup-issue")
        if not errors:
            status = "PASS_BYPASS"
            bypass_applied = True

    result = {
        "schemaVersion": 1,
        "evaluatedAtUtc": now.replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "phase": phase,
        "status": status,
        "gateState": gate_state,
        "cutoffUtc": cutoff.isoformat().replace("+00:00", "Z"),
        "baseline": str(args.baseline),
        "candidate": str(args.candidate),
        "regressions": regressions,
        "errors": errors,
        "bypass": {
            "applied": bypass_applied,
            "reason": args.bypass_reason,
            "followupIssue": args.followup_issue,
        },
    }

    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))

    return 0 if status in {"PASS", "WARN", "PASS_BYPASS"} else 1


if __name__ == "__main__":
    raise SystemExit(main())
