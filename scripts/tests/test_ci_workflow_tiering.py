from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read_workflow(name: str) -> str:
    return (ROOT / ".github" / "workflows" / name).read_text(encoding="utf-8")


class CiWorkflowTieringTest(unittest.TestCase):
    def test_branch_push_ci_only_runs_on_main(self) -> None:
        workflow = read_workflow("build_push.yml")

        self.assertIn("branches:\n      - main", workflow)
        self.assertNotIn("branches:\n      - '*'", workflow)

    def test_emulator_workflows_are_removed(self) -> None:
        self.assertFalse((ROOT / ".github" / "workflows" / "theme_instrumentation_pr.yml").exists())

    def test_plugin_compatibility_validates_its_own_workflow(self) -> None:
        workflow = read_workflow("plugin_compatibility.yml")

        self.assertIn("'.github/workflows/plugin_compatibility.yml'", workflow)

    def test_gitleaks_scans_pull_request_head_history(self) -> None:
        workflow = read_workflow("secret_scan.yml")

        self.assertIn("ref: ${{ github.event.pull_request.head.sha || github.sha }}", workflow)
        self.assertIn("fetch-depth: 0", workflow)

    def test_pr_build_skips_gradle_for_non_android_changes(self) -> None:
        workflow = read_workflow("build_pull_request.yml")

        self.assertIn("Classify PR changes", workflow)
        self.assertIn("android_changed=false", workflow)
        self.assertIn("docs/**|.github/**|scripts/tests/**|*.md", workflow)
        self.assertIn("if: needs.classify_pr_changes.outputs.android_changed == 'true'", workflow)

    def test_ci_tiering_decisions_are_documented(self) -> None:
        doc = ROOT / "docs" / "ci-tiering.md"

        self.assertTrue(doc.exists())
        text = doc.read_text(encoding="utf-8")
        self.assertIn("Required PR Gate", text)
        self.assertIn("Removed Emulator Gate", text)
        self.assertIn("Release Gate", text)
        self.assertIn("pull request head SHA", text)
        self.assertIn("SqlDelight bootstrap", text)


if __name__ == "__main__":
    unittest.main()
