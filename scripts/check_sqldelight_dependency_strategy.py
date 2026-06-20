#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


FORBIDDEN_LITERAL_PATTERNS = (
    "SQLDELIGHT_AGP9_FIX",
    "Build SqlDelight AGP 9 fix",
    "Download SqlDelight local Maven artifacts",
    "Prepare SqlDelight artifacts",
    "sqldelight-m2-",
)

FORBIDDEN_REGEX_PATTERNS = (
    (
        re.compile(r'(?im)^\s*sqldelight\s*=\s*"[^"]*-SNAPSHOT"\s*$'),
        'SqlDelight version must not use a SNAPSHOT artifact',
    ),
    (
        re.compile(r'(?i)central\.sonatype\.com/repository/maven-snapshots'),
        'Central Portal snapshots repository must not be used for SqlDelight',
    ),
    (
        re.compile(r'(?is)mavenLocal\s*\{[^}]*includeGroup\("app\.cash\.sqldelight"\)'),
        'mavenLocal must not be used for app.cash.sqldelight artifacts',
    ),
)

SCAN_PATHS = (
    "gradle/libs.versions.toml",
    "settings.gradle.kts",
    ".github/workflows",
)


def iter_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for relative in SCAN_PATHS:
        path = root / relative
        if path.is_file():
            files.append(path)
        elif path.is_dir():
            files.extend(sorted(path.glob("*.yml")))
            files.extend(sorted(path.glob("*.yaml")))
    return files


def violations(root: Path) -> list[str]:
    found: list[str] = []
    for path in iter_files(root):
        text = path.read_text(encoding="utf-8")
        for pattern in FORBIDDEN_LITERAL_PATTERNS:
            if pattern in text:
                found.append(f"{path.relative_to(root)} contains {pattern}")
        for pattern, message in FORBIDDEN_REGEX_PATTERNS:
            if pattern.search(text):
                found.append(f"{path.relative_to(root)} violates policy: {message}")
    return found


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".", help="Repository root to scan")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    found = violations(root)
    if found:
        print("SqlDelight snapshot/bootstrap workaround is still present:", file=sys.stderr)
        for violation in found:
            print(f"- {violation}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
