#!/usr/bin/env python3
"""Reject random or wall-clock values in Compose preview code."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from typing import NamedTuple, Sequence
from pathlib import Path

SOURCE_ROOTS = (
    Path("app/src/main"),
    Path("presentation-core/src/main"),
)

PREVIEW_MARKER = re.compile(r"^\s*@Preview\w*|PreviewParameterProvider", re.MULTILINE)
PREVIEW_FILE_NAME = re.compile(r"Preview\w*\.kt$")

NONDETERMINISTIC = re.compile(r"\bRandom\b|\.random\(|\bnow\(\)|currentTimeMillis")


class AllowedLine(NamedTuple):
    path: str
    line: str
    reason: str

    def matches(self, path: str, line: str) -> bool:
        return bool(re.fullmatch(self.path, path) and re.fullmatch(self.line, line))


ALLOWED = (
    AllowedLine(
        path=r"app/src/main/java/eu/kanade/presentation/entries/components/EntryInfoHeader\.kt",
        line=r"val now = Instant\.now\(\)",
        reason="Production code. The previews pass nextUpdate = null, so the line never runs.",
    ),
)


@dataclass(frozen=True)
class Violation:
    path: str
    line: int
    text: str


def _is_preview_file(path: Path, text: str) -> bool:
    return bool(PREVIEW_FILE_NAME.search(path.name) or PREVIEW_MARKER.search(text))


def _is_ignored(stripped: str) -> bool:
    return stripped.startswith(("import ", "//", "/*", "*"))


def _scan(root: Path, allowed: Sequence[AllowedLine]) -> tuple[list[Violation], set[AllowedLine]]:
    violations: list[Violation] = []
    used: set[AllowedLine] = set()
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
                entry = next((e for e in allowed if e.matches(relative, stripped)), None)
                if entry is None:
                    violations.append(Violation(relative, number, stripped))
                else:
                    used.add(entry)
    return violations, used


def find_violations(root: Path, allowed: Sequence[AllowedLine]) -> list[Violation]:
    return _scan(root, allowed)[0]


def find_stale_entries(root: Path, allowed: Sequence[AllowedLine]) -> list[AllowedLine]:
    used = _scan(root, allowed)[1]
    return [entry for entry in allowed if entry not in used]


def main(argv: list[str] | None = None, allowed: Sequence[AllowedLine] = ALLOWED) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args(argv)

    root = args.root.resolve()
    violations = find_violations(root, allowed)
    stale = find_stale_entries(root, allowed)
    for violation in violations:
        print(f"{violation.path}:{violation.line}: {violation.text}")
    for entry in stale:
        print(f"stale ALLOWED entry: {entry.path}\t{entry.line}")
    if violations:
        print(
            "Previews must render the same on every run. Use fixed values, or add a reasoned "
            "AllowedLine to ALLOWED in scripts/check_preview_determinism.py.",
            file=sys.stderr,
        )
    return 1 if violations or stale else 0


if __name__ == "__main__":
    raise SystemExit(main())
