from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.check_sqldelight_dependency_strategy import main as check_main


class SqlDelightDependencyStrategyTest(unittest.TestCase):
    def _run(self, root: Path) -> int:
        import sys

        argv = sys.argv
        try:
            sys.argv = [
                "check_sqldelight_dependency_strategy.py",
                "--root",
                str(root),
            ]
            return check_main()
        finally:
            sys.argv = argv

    def test_rejects_snapshot_version_and_ci_bootstrap(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "gradle").mkdir()
            (root / ".github" / "workflows").mkdir(parents=True)
            (root / "gradle" / "libs.versions.toml").write_text(
                'sqldelight = "2.3.3-SNAPSHOT"\n',
                encoding="utf-8",
            )
            (root / "settings.gradle.kts").write_text(
                """
                repositories {
                    mavenLocal {
                        content {
                            includeGroup("app.cash.sqldelight")
                        }
                    }
                    maven(url = "https://central.sonatype.com/repository/maven-snapshots/")
                }
                """,
                encoding="utf-8",
            )
            (root / ".github" / "workflows" / "build.yml").write_text(
                "env:\n  SQLDELIGHT_AGP9_FIX_SHA: abc\n",
                encoding="utf-8",
            )

            self.assertEqual(self._run(root), 1)

    def test_accepts_released_sqldelight_without_bootstrap(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "gradle").mkdir()
            (root / ".github" / "workflows").mkdir(parents=True)
            (root / "gradle" / "libs.versions.toml").write_text(
                'sqldelight = "2.3.2"\n',
                encoding="utf-8",
            )
            (root / "settings.gradle.kts").write_text(
                "repositories { mavenCentral(); google() }\n",
                encoding="utf-8",
            )
            (root / ".github" / "workflows" / "build.yml").write_text(
                "steps:\n  - run: ./gradlew spotlessCheck\n",
                encoding="utf-8",
            )

            self.assertEqual(self._run(root), 0)


if __name__ == "__main__":
    unittest.main()
