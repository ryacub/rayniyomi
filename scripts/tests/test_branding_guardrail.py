from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.check_branding_guardrail import main as guardrail_main


class BrandingGuardrailTest(unittest.TestCase):
    def setUp(self) -> None:
        self.allowlist = Path("scripts/branding_guardrail_allowlist.txt")

    def _run(self, diff_text: str, allowlist_text: str | None = None) -> int:
        with tempfile.TemporaryDirectory() as tmp:
            tmpdir = Path(tmp)
            diff_file = tmpdir / "diff.patch"
            diff_file.write_text(diff_text, encoding="utf-8")

            allowlist_file = tmpdir / "allowlist.txt"
            if allowlist_text is None:
                allowlist_file.write_text(self.allowlist.read_text(encoding="utf-8"), encoding="utf-8")
            else:
                allowlist_file.write_text(allowlist_text, encoding="utf-8")

            import sys

            argv = sys.argv
            try:
                sys.argv = [
                    "check_branding_guardrail.py",
                    "--base",
                    "origin/main",
                    "--allowlist",
                    str(allowlist_file),
                    "--diff-file",
                    str(diff_file),
                ]
                return guardrail_main()
            finally:
                sys.argv = argv

    def test_fails_on_forbidden_added_line_in_scoped_file(self) -> None:
        diff = """\
diff --git a/app/src/main/java/foo.kt b/app/src/main/java/foo.kt
index 1111111..2222222 100644
--- a/app/src/main/java/foo.kt
+++ b/app/src/main/java/foo.kt
@@ -1 +1 @@
+val x = "Aniyomi"
"""
        self.assertEqual(self._run(diff), 1)

    def test_passes_when_forbidden_token_only_in_removed_or_unchanged_lines(self) -> None:
        diff = """\
diff --git a/app/src/main/java/foo.kt b/app/src/main/java/foo.kt
index 1111111..2222222 100644
--- a/app/src/main/java/foo.kt
+++ b/app/src/main/java/foo.kt
@@ -1,2 +1 @@
-val x = "Aniyomi"
 val y = "ok"
"""
        self.assertEqual(self._run(diff), 0)

    def test_passes_when_allowlisted(self) -> None:
        diff = """\
diff --git a/README.md b/README.md
index 1111111..2222222 100644
--- a/README.md
+++ b/README.md
@@ -1 +1,2 @@
 Test
+Copyright (c) 2024 Aniyomi Open Source Project
"""
        allowlist = r"README\.md\tCopyright .+ Aniyomi Open Source Project\tAttribution\n"
        self.assertEqual(self._run(diff, allowlist), 0)

    def test_ignores_excluded_path(self) -> None:
        diff = """\
diff --git a/.github/ISSUE_TEMPLATE/report_issue.yml b/.github/ISSUE_TEMPLATE/report_issue.yml
index 1111111..2222222 100644
--- a/.github/ISSUE_TEMPLATE/report_issue.yml
+++ b/.github/ISSUE_TEMPLATE/report_issue.yml
@@ -1 +1,2 @@
 title: Issue
+description: Aniyomi issue template
"""
        self.assertEqual(self._run(diff), 0)

    def test_handles_rename_diff_hunks(self) -> None:
        diff = """\
diff --git a/docs/old.md b/docs/new.md
similarity index 95%
rename from docs/old.md
rename to docs/new.md
index 1111111..2222222 100644
--- a/docs/old.md
+++ b/docs/new.md
@@ -1 +1,2 @@
 heading
+see https://aniyomi.org/docs
"""
        self.assertEqual(self._run(diff), 1)


if __name__ == "__main__":
    unittest.main()
