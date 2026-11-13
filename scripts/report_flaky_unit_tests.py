#!/usr/bin/env python3
"""Report unit tests that failed and later passed on the same commit in PR CI."""

import argparse
from collections import defaultdict
import json
import re
import subprocess
import sys

WORKFLOW = "build_pull_request.yml"
JOB_NAME = "Run unit tests"
TEST_RESULT = re.compile(r"^\S+Z (?P<test>\S+ > .+?) (?P<result>FAILED|PASSED)$", re.MULTILINE)


def gh(*args):
    result = subprocess.run(["gh", *args], capture_output=True, text=True, check=False)
    if result.returncode:
        raise RuntimeError(result.stderr.strip())
    return result.stdout


def test_results(log):
    results = defaultdict(set)
    for match in TEST_RESULT.finditer(log):
        results[match["result"]].add(match["test"])
    return results


def report(repo, limit):
    runs = json.loads(gh(
        "run", "list", "--repo", repo, "--workflow", WORKFLOW, "--limit", str(limit),
        "--json", "databaseId,headSha,conclusion,attempt",
    ))
    candidate_shas = {
        run["headSha"] for run in runs
        if run["conclusion"] != "success" or run["attempt"] > 1
    }
    jobs_by_sha = defaultdict(list)
    for run in runs:
        sha = run["headSha"]
        if sha not in candidate_shas:
            continue
        endpoint = f"repos/{repo}/actions/runs/{run['databaseId']}/jobs?filter=all&per_page=100"
        jobs = json.loads(gh("api", "--paginate", "--slurp", endpoint))
        jobs_by_sha[sha].extend(
            job for page in jobs for job in page["jobs"]
            if job["name"] == JOB_NAME and job["conclusion"] in ("failure", "success")
        )

    matched = defaultdict(list)
    missing = 0
    for jobs in jobs_by_sha.values():
        ordered = sorted(jobs, key=lambda job: (job["started_at"], job["id"]))
        if not any(job["conclusion"] == "failure" for job in ordered):
            continue
        passed = set()
        for job in reversed(ordered):
            try:
                log = gh("api", f"repos/{repo}/actions/jobs/{job['id']}/logs")
            except RuntimeError as error:
                print(f"Job {job['id']}: {error}", file=sys.stderr)
                missing += 1
                continue
            results = test_results(log)
            for test in results["FAILED"] & passed:
                matched[test].append(job["started_at"][:10])
            passed.update(results["PASSED"])

    print(f"Scanned {len(runs)} PR workflow runs. Unavailable job logs: {missing}.")
    print("Same-commit failures later passed\tFirst failure\tLast failure\tTest")
    for test, dates in sorted(matched.items(), key=lambda item: (-len(item[1]), item[0])):
        print(f"{len(dates)}\t{min(dates)}\t{max(dates)}\t{test}")
    return int(missing > 0)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="ryacub/rayniyomi", help="GitHub repository")
    parser.add_argument("--limit", type=int, default=400, help="recent workflow runs to check")
    args = parser.parse_args()
    if args.limit < 1:
        parser.error("--limit must be positive")
    try:
        return report(args.repo, args.limit)
    except (OSError, RuntimeError, ValueError) as error:
        print(f"Cannot read GitHub Actions history: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
