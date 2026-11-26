#!/usr/bin/env python3
"""Reject random or wall-clock values in Compose preview code."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path

SOURCE_ROOTS = (
    Path("app/src/main"),
    Path("presentation-core/src/main"),
)

DEFAULT_ALLOWLIST = Path(__file__).resolve().parent / "preview_determinism_allowlist.txt"

PREVIEW_MARKER = re.compile(r"^\s*@Preview\w*|PreviewParameterProvider", re.MULTILINE)
PREVIEW_FILE_NAME = re.compile(r"Preview\w*\.kt$")

NONDETERMINISTIC = re.compile(r"\bRandom\b|\.random\(|\bnow\(\)|currentTimeMillis")


@dataclass(frozen=True)
class AllowlistEntry:
    path_regex: re.Pattern[str]
    line_regex: re.Pattern[str]
    reason: str

    @classmethod
    def build(cls, path_regex: str, line_regex: str, reason: str) -> AllowlistEntry:
        if not reason.strip():
            raise ValueError(f"allow-list entry for {path_regex!r} has no reason")
        return cls(re.compile(path_regex), re.compile(line_regex), reason.strip())

    def matches(self, path: str, line: str) -> bool:
        return bool(self.path_regex.fullmatch(path) and self.line_regex.fullmatch(line))


@dataclass(frozen=True)
class Violation:
    path: str
    line: int
    text: str


def load_allowlist(path: Path) -> list[AllowlistEntry]:
    entries: list[AllowlistEntry] = []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        if not raw.strip() or raw.startswith("#"):
            continue
        fields = raw.split("\t")
        if len(fields) != 3:
            raise ValueError(f"{path}:{number}: expected path_regex<TAB>line_regex<TAB>reason")
        entries.append(AllowlistEntry.build(*fields))
    return entries


def _is_preview_file(path: Path, text: str) -> bool:
    return bool(PREVIEW_FILE_NAME.search(path.name) or PREVIEW_MARKER.search(text))


def _is_ignored(stripped: str) -> bool:
    return stripped.startswith(("import ", "//", "/*", "*"))


def _scan(root: Path, allowlist: list[AllowlistEntry]) -> tuple[list[Violation], set[AllowlistEntry]]:
    violations: list[Violation] = []
    used: set[AllowlistEntry] = set()
    for source_root in SOURCE_ROOTS:
        absolute_root = root / source_root
        if not absolute_root.exists():
            continue
        for path in sorted(absolute_root.rglob("*.kt")):
            text = path.read_text(encoding="utf-8")
            if not _is_preview_file(path, text):
                continue
            relative = path.relative_to(root).as_posix()
            for number, line in enumerate(text.splitlines(), start=1):
                stripped = line.strip()
                if _is_ignored(stripped) or not NONDETERMINISTIC.search(stripped):
                    continue
                entry = next((e for e in allowlist if e.matches(relative, stripped)), None)
                if entry is None:
                    violations.append(Violation(relative, number, stripped))
                else:
                    used.add(entry)
    return violations, used


def find_violations(root: Path, allowlist: list[AllowlistEntry]) -> list[Violation]:
    return _scan(root, allowlist)[0]


def find_stale_entries(root: Path, allowlist: list[AllowlistEntry]) -> list[AllowlistEntry]:
    used = _scan(root, allowlist)[1]
    return [entry for entry in allowlist if entry not in used]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--allowlist", type=Path, default=DEFAULT_ALLOWLIST)
    args = parser.parse_args(argv)

    root = args.root.resolve()
    try:
        allowlist = load_allowlist(args.allowlist)
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1

    violations = find_violations(root, allowlist)
    stale = find_stale_entries(root, allowlist)
    for violation in violations:
        print(f"{violation.path}:{violation.line}: {violation.text}")
    for entry in stale:
        print(f"stale allow-list entry: {entry.path_regex.pattern}\t{entry.line_regex.pattern}")
    if violations:
        print(
            "Previews must render the same on every run. Use fixed values, or add a reasoned entry "
            "to scripts/preview_determinism_allowlist.txt.",
            file=sys.stderr,
        )
    return 1 if violations or stale else 0


if __name__ == "__main__":
    raise SystemExit(main())
