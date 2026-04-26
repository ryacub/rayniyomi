#!/usr/bin/env python3
"""Fail PRs when newly added user-facing lines regress to upstream Aniyomi branding."""

from __future__ import annotations

import argparse
import fnmatch
import re
import subprocess
import sys
import unicodedata
from dataclasses import dataclass
from pathlib import Path

FORBIDDEN_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("Aniyomi", re.compile(r"\bAniyomi\b", re.IGNORECASE)),
    ("aniyomi.org", re.compile(r"aniyomi\.org", re.IGNORECASE)),
]

INCLUDE_GLOBS = [
    "app/src/main/**",
    "i18n/src/commonMain/moko-resources/base/strings.xml",
    "i18n/src/commonMain/moko-resources/base/plurals.xml",
    "README*",
    "docs/**",
]

EXCLUDE_GLOBS = [
    ".github/**",
    "**/strings-aniyomi.xml",
    "**/plurals-aniyomi.xml",
]


@dataclass(frozen=True)
class AllowlistEntry:
    path_regex: re.Pattern[str]
    token_regex: re.Pattern[str]
    reason: str


@dataclass(frozen=True)
class AddedLine:
    path: str
    line_number: int
    text: str


@dataclass(frozen=True)
class Violation:
    path: str
    line_number: int
    token: str
    line_text: str


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, help="Base commit/ref used for git diff <base>...HEAD")
    parser.add_argument("--allowlist", default="scripts/branding_guardrail_allowlist.txt")
    parser.add_argument("--diff-file", help="Optional path to a unified diff fixture")
    return parser.parse_args()


def normalize_path(path: str) -> str:
    return path.replace("\\", "/")


def is_scoped_path(path: str) -> bool:
    normalized = normalize_path(path)
    included = any(fnmatch.fnmatch(normalized, pattern) for pattern in INCLUDE_GLOBS)
    excluded = any(fnmatch.fnmatch(normalized, pattern) for pattern in EXCLUDE_GLOBS)
    return included and not excluded


def load_allowlist(path: Path) -> list[AllowlistEntry]:
    entries: list[AllowlistEntry] = []
    if not path.exists():
        return entries
    for index, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "\t" in raw:
            parts = raw.split("\t")
        else:
            parts = raw.split("\\t")
        if len(parts) != 3:
            raise ValueError(
                f"Invalid allowlist format at {path}:{index}. "
                "Expected '<path_regex>\\t<token_regex>\\t<reason>'.",
            )
        path_regex, token_regex, reason = parts
        entries.append(
            AllowlistEntry(
                path_regex=re.compile(path_regex),
                token_regex=re.compile(token_regex),
                reason=reason.strip(),
            ),
        )
    return entries


def read_diff(base: str, diff_file: str | None) -> str:
    if diff_file:
        return Path(diff_file).read_text(encoding="utf-8")
    cmd = ["git", "diff", "--unified=0", "--no-color", "--find-renames", f"{base}...HEAD"]
    result = subprocess.run(cmd, check=False, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(f"git diff failed with exit code {result.returncode}: {result.stderr.strip()}")
    return result.stdout


def parse_added_lines(diff_text: str) -> list[AddedLine]:
    added: list[AddedLine] = []
    current_path: str | None = None
    current_new_line = 0
    line_re = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@")

    for raw in diff_text.splitlines():
        if raw.startswith("+++ "):
            marker = raw[4:]
            if marker == "/dev/null":
                current_path = None
            elif marker.startswith("b/"):
                current_path = normalize_path(marker[2:])
            else:
                current_path = normalize_path(marker)
            continue
        if raw.startswith("@@ "):
            match = line_re.match(raw)
            current_new_line = int(match.group(1)) if match else 0
            continue
        if current_path is None:
            continue
        if raw.startswith("+") and not raw.startswith("+++"):
            added.append(AddedLine(path=current_path, line_number=current_new_line, text=raw[1:]))
            current_new_line += 1
            continue
        if raw.startswith(" "):
            current_new_line += 1
    return added


def is_allowlisted(path: str, text: str, entries: list[AllowlistEntry]) -> bool:
    for entry in entries:
        if entry.path_regex.search(path) and entry.token_regex.search(text):
            return True
    return False


def find_violations(added_lines: list[AddedLine], allowlist: list[AllowlistEntry]) -> list[Violation]:
    violations: list[Violation] = []
    for line in added_lines:
        if not is_scoped_path(line.path):
            continue
        normalized = unicodedata.normalize("NFKC", line.text)
        if is_allowlisted(line.path, normalized, allowlist):
            continue
        for token, pattern in FORBIDDEN_PATTERNS:
            if pattern.search(normalized):
                violations.append(
                    Violation(
                        path=line.path,
                        line_number=line.line_number,
                        token=token,
                        line_text=line.text.strip(),
                    ),
                )
                break
    return violations


def main() -> int:
    args = parse_args()
    try:
        allowlist = load_allowlist(Path(args.allowlist))
        diff_text = read_diff(args.base, args.diff_file)
        added_lines = parse_added_lines(diff_text)
        violations = find_violations(added_lines, allowlist)
    except Exception as exc:  # noqa: BLE001
        print(f"Branding guardrail failed: {exc}", file=sys.stderr)
        return 2

    if not violations:
        print("Branding guardrail passed: no new forbidden branding tokens in scoped added lines.")
        return 0

    print("Branding guardrail violations:")
    for violation in violations:
        print(
            f"- {violation.path}:{violation.line_number} token={violation.token} "
            f"line={violation.line_text!r}",
        )
    print(
        "\nFix: replace upstream branding in user-facing content, or add a narrow path+token exception "
        "to scripts/branding_guardrail_allowlist.txt when intentional.",
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
