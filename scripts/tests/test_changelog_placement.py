from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

from scripts.check_changelog_placement import main as placement_main


CHANGELOG = """\
# Changelog

## Unreleased

### Added

### Fixed

## [1.2.3] - 2026-01-01

### Added
- shipped entry

### Fixed
"""


def diff_adding(lineno: int, content: str) -> str:
    return (
        "diff --git a/CHANGELOG.md b/CHANGELOG.md\n"
        "--- a/CHANGELOG.md\n"
        "+++ b/CHANGELOG.md\n"
        f"@@ -{lineno},0 +{lineno} @@\n"
        f"+{content}\n"
    )


class ChangelogPlacementTest(unittest.TestCase):
    def _run(self, diff: str, changelog: str = CHANGELOG) -> int:
        with tempfile.TemporaryDirectory() as tmp:
            tmpdir = Path(tmp)
            changelog_file = tmpdir / "CHANGELOG.md"
            changelog_file.write_text(changelog, encoding="utf-8")
            diff_file = tmpdir / "diff.patch"
            diff_file.write_text(diff, encoding="utf-8")

            argv = sys.argv
            try:
                sys.argv = [
                    "check_changelog_placement.py",
                    "--base",
                    "origin/main",
                    "--file",
                    str(changelog_file),
                    "--diff-file",
                    str(diff_file),
                ]
                return placement_main()
            finally:
                sys.argv = argv

    def test_passes_when_entry_is_under_unreleased(self) -> None:
        # line 6 is inside the Unreleased section (lines 3..8)
        self.assertEqual(0, self._run(diff_adding(6, "- new entry")))

    def test_fails_when_entry_is_under_released_heading(self) -> None:
        # line 12 sits under "## [1.2.3]"
        self.assertEqual(1, self._run(diff_adding(12, "- misfiled entry")))

    def test_passes_when_changelog_untouched(self) -> None:
        self.assertEqual(0, self._run(""))

    def test_ignores_added_section_headings(self) -> None:
        self.assertEqual(0, self._run(diff_adding(12, "### Changed")))

    def test_ignores_added_blank_lines(self) -> None:
        self.assertEqual(0, self._run(diff_adding(12, "")))

    def test_reports_tooling_failure_when_unreleased_heading_missing(self) -> None:
        self.assertEqual(
            2,
            self._run(diff_adding(3, "- entry"), changelog="# Changelog\n\n## [1.2.3]\n\n- x\n"),
        )

    def test_counts_multiple_added_lines_in_one_hunk(self) -> None:
        diff = (
            "diff --git a/CHANGELOG.md b/CHANGELOG.md\n"
            "--- a/CHANGELOG.md\n"
            "+++ b/CHANGELOG.md\n"
            "@@ -6,0 +6,2 @@\n"
            "+- first entry\n"
            "+- second entry\n"
        )
        self.assertEqual(0, self._run(diff))


if __name__ == "__main__":
    unittest.main()
