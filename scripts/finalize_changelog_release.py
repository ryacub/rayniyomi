#!/usr/bin/env python3
"""
Finalize CHANGELOG.md for a tagged release by rolling Unreleased forward.

Behavior:
- If release section `## [<version>]` already exists: no-op.
- Move the first `## Unreleased` section body into `## [<version>] - <date>`.
- Recreate an empty `## Unreleased` template using existing subsection headings.
"""

from __future__ import annotations

import argparse
import datetime as dt
import re
import sys
from pathlib import Path


DEFAULT_HEADINGS = [
    "### Added",
    "### Fixed",
    "### Changed",
    "### CI",
    "### Other",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("version", help="Release version, with or without leading 'v'")
    parser.add_argument(
        "--date",
        default=dt.date.today().isoformat(),
        help="Release date in YYYY-MM-DD format (default: today)",
    )
    parser.add_argument(
        "--file",
        default="CHANGELOG.md",
        help="Path to changelog file (default: CHANGELOG.md)",
    )
    return parser.parse_args()


def find_unreleased_bounds(lines: list[str]) -> tuple[int, int]:
    start = -1
    for i, line in enumerate(lines):
        if line.strip() == "## Unreleased":
            start = i
            break
    if start == -1:
        raise ValueError("Could not find '## Unreleased' section.")

    end = len(lines)
    for i in range(start + 1, len(lines)):
        if lines[i].startswith("## "):
            end = i
            break
    return start, end


def main() -> int:
    args = parse_args()
    version = args.version.lstrip("v")
    changelog_path = Path(args.file)
    text = changelog_path.read_text(encoding="utf-8")
    lines = text.splitlines()

    if re.search(rf"^## \[{re.escape(version)}\](?:\s|$)", text, flags=re.MULTILINE):
        print(f"Version section [{version}] already exists; no changes.")
        return 0

    start, end = find_unreleased_bounds(lines)
    before = lines[:start]
    unreleased_body = lines[start + 1 : end]
    after = lines[end:]

    headings = [line for line in unreleased_body if line.startswith("### ")]
    if not headings:
        headings = DEFAULT_HEADINGS

    unreleased_text = "## Unreleased\n\n" + "\n\n".join(headings) + "\n"

    release_body = "\n".join(unreleased_body).strip("\n")
    if not release_body:
        release_body = "### Other\n\n- No user-facing changes."
    release_text = f"## [{version}] - {args.date}\n\n{release_body}\n"

    before_text = "\n".join(before).rstrip("\n")
    after_text = "\n".join(after).lstrip("\n")
    pieces = [before_text, unreleased_text, release_text, after_text]
    new_text = "\n\n".join(p for p in pieces if p.strip()) + "\n"

    changelog_path.write_text(new_text, encoding="utf-8")
    print(f"Updated {changelog_path} for release {version} ({args.date}).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
