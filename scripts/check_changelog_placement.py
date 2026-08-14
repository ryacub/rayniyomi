#!/usr/bin/env python3
"""
Fail when a pull request adds changelog entries outside `## Unreleased`.

`finalize_changelog_release.py` keeps the `## Unreleased` heading pinned and inserts the
new release heading below it, so entries stay put while the heading above them changes.
A branch cut before a release therefore anchors its bullet to the released section, and
git's three-way merge silently files it under the shipped version.
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path


HUNK_RE = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True, help="Base ref to diff against")
    parser.add_argument(
        "--file",
        default="CHANGELOG.md",
        help="Path to changelog file (default: CHANGELOG.md)",
    )
    parser.add_argument(
        "--diff-file",
        default=None,
        help="Read the unified diff from this file instead of running git (for tests)",
    )
    return parser.parse_args()


def diff_text(base: str, path: str, diff_file: str | None) -> str:
    if diff_file is not None:
        try:
            return Path(diff_file).read_text(encoding="utf-8")
        except OSError as exc:
            raise RuntimeError(f"could not read diff file: {exc}") from exc

    try:
        result = subprocess.run(
            ["git", "diff", "--unified=0", f"{base}...HEAD", "--", path],
            capture_output=True,
            text=True,
            check=False,
        )
    except OSError as exc:
        raise RuntimeError(f"could not run git diff: {exc}") from exc
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip() or "git diff failed")
    return result.stdout


def added_lines(diff: str) -> list[tuple[int, str]]:
    """Return (line number in the new file, content) for each added line."""
    added: list[tuple[int, str]] = []
    lineno = 0
    for line in diff.splitlines():
        hunk = HUNK_RE.match(line)
        if hunk:
            lineno = int(hunk.group(1))
            continue
        if line.startswith("+++"):
            continue
        if line.startswith("+"):
            added.append((lineno, line[1:]))
            lineno += 1
    return added


def unreleased_bounds(lines: list[str]) -> tuple[int, int]:
    """Return the 1-based half-open line range of the `## Unreleased` section."""
    start = -1
    for i, line in enumerate(lines):
        if line.strip() == "## Unreleased":
            start = i
            break
    if start == -1:
        raise RuntimeError("could not find '## Unreleased' section")

    end = len(lines)
    for i in range(start + 1, len(lines)):
        if lines[i].startswith("## "):
            end = i
            break
    return start + 1, end + 1


def main() -> int:
    args = parse_args()

    try:
        added = added_lines(diff_text(args.base, args.file, args.diff_file))
    except RuntimeError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    if not added:
        print(f"{args.file} not modified; nothing to check.")
        return 0

    try:
        lines = Path(args.file).read_text(encoding="utf-8").splitlines()
        start, end = unreleased_bounds(lines)
    except (OSError, RuntimeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2

    violations = [
        (lineno, content)
        for lineno, content in added
        if content.strip()
        and not content.lstrip().startswith("#")
        and not start <= lineno < end
    ]

    if violations:
        for lineno, content in violations:
            print(f"{args.file}:{lineno}: {content.strip()}")
        print(
            f"\nAdd changelog entries under '## Unreleased', not under a released "
            f"version heading. The Unreleased section spans lines {start}-{end - 1}.\n"
            f"A release rolls Unreleased forward without moving existing entries, so a "
            f"branch cut before the release can land its bullet in the shipped section.",
            file=sys.stderr,
        )
        return 1

    print(f"{args.file}: all added entries are under '## Unreleased'.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
