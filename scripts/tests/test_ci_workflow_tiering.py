from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def read_workflow(name: str) -> str:
    return (ROOT / ".github" / "workflows" / name).read_text(encoding="utf-8")


def read_job(workflow: str, job_id: str) -> str:
    match = re.search(rf"\n  {job_id}:\n(.*?)(?=\n  [a-z_]+:\n|\Z)", workflow, re.DOTALL)
    assert match is not None, f"job {job_id} is missing"
    return match.group(1)


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

    def test_pr_build_runs_gate_script_suite(self) -> None:
        workflow = read_workflow("build_pull_request.yml")

        self.assertIn(
            "python3 -m unittest discover -s scripts/tests -p 'test_*.py'",
            workflow,
        )

    def test_pr_build_verifies_preview_screenshots_in_a_parallel_job(self) -> None:
        workflow = read_workflow("build_pull_request.yml")
        preview_job = read_job(workflow, "preview_screenshots")
        test_job = read_job(workflow, "test")

        self.assertIn("name: Verify preview screenshots", preview_job)
        self.assertIn("needs: [classify_pr_changes]", preview_job)
        self.assertIn("if: needs.classify_pr_changes.outputs.android_changed == 'true'", preview_job)
        self.assertIn("python3 scripts/check_preview_determinism.py", preview_job)
        self.assertIn("-Proborazzi.test.verify=true", preview_job)
        self.assertIn("-Prayniyomi.previewScreenshots=true", preview_job)
        self.assertIn("--tests '*RoborazziPreviewParameterizedTests*'", preview_job)
        self.assertIn("app/build/outputs/roborazzi/", preview_job)
        self.assertIn("presentation-core/build/outputs/roborazzi/", preview_job)
        self.assertNotIn("Verify shared Compose screenshots", test_job)

    def test_ci_tiering_decisions_are_documented(self) -> None:
        doc = ROOT / "docs" / "ci-tiering.md"

        self.assertTrue(doc.exists())
        text = doc.read_text(encoding="utf-8")
        self.assertIn("Required PR Gate", text)
        self.assertIn("Removed Emulator Gate", text)
        self.assertIn("Release Gate", text)
        self.assertIn("pull request head SHA", text)
        self.assertIn("SqlDelight bootstrap", text)
        self.assertIn("Verify preview screenshots", text)
        self.assertIn("-Prayniyomi.previewScreenshots=true", text)


if __name__ == "__main__":
    unittest.main()
