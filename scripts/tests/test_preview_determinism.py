from __future__ import annotations

import re
import tempfile
import unittest
from pathlib import Path

from scripts.check_preview_determinism import (
    ALLOWED,
    AllowedLine,
    find_stale_entries,
    find_violations,
    main,
)


def write(root: Path, relative: str, text: str) -> None:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")


PREVIEW_FILE = "app/src/main/java/example/Screen.kt"


class PreviewDeterminismTest(unittest.TestCase):
    def check(self, files: dict[str, str], allowlist: list[AllowedLine] | None = None):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for relative, text in files.items():
                write(root, relative, text)
            return find_violations(root, allowlist or [])

    def test_flags_random_in_a_file_with_a_preview(self) -> None:
        violations = self.check(
            {
                PREVIEW_FILE: "@Preview\n@Composable\nfun P() {\n    val n = Random.nextInt()\n}\n",
            },
        )

        self.assertEqual([(v.path, v.line) for v in violations], [(PREVIEW_FILE, 4)])

    def test_flags_now_in_a_preview_fixtures_file(self) -> None:
        path = "app/src/main/java/example/TrackPreviewFixtures.kt"

        violations = self.check({path: "val month = YearMonth.now()\n"})

        self.assertEqual([(v.path, v.line) for v in violations], [(path, 1)])

    def test_flags_random_pick_in_a_preview_parameter_provider(self) -> None:
        violations = self.check(
            {
                "presentation-core/src/main/java/example/Provider.kt": (
                    "class P : PreviewParameterProvider<Int> {\n"
                    "    override val values = sequenceOf(1, 2).map { listOf(it).random() }\n"
                    "}\n"
                ),
            },
        )

        self.assertEqual([v.line for v in violations], [2])

    def test_flags_current_time_millis(self) -> None:
        violations = self.check({PREVIEW_FILE: "@Preview\nval t = System.currentTimeMillis()\n"})

        self.assertEqual([v.line for v in violations], [2])

    def test_ignores_random_in_a_file_without_a_preview_marker(self) -> None:
        self.assertEqual(self.check({PREVIEW_FILE: "val n = Random.nextInt()\n"}), [])

    def test_ignores_import_lines(self) -> None:
        self.assertEqual(
            self.check({PREVIEW_FILE: "import kotlin.random.Random\n@Preview\nfun P() {}\n"}),
            [],
        )

    def test_allowlist_entry_suppresses_a_matching_line(self) -> None:
        entry = AllowedLine(PREVIEW_FILE, r"val n = Random\(0\)\.nextInt\(\)", "seeded")

        violations = self.check(
            {PREVIEW_FILE: "@Preview\nval n = Random(0).nextInt()\nval m = Random.nextInt()\n"},
            [entry],
        )

        self.assertEqual([v.line for v in violations], [3])

    def test_allowlist_entry_must_match_the_whole_line(self) -> None:
        entry = AllowedLine(PREVIEW_FILE, r"Random\(0\)", "seeded")

        violations = self.check(
            {PREVIEW_FILE: "@Preview\nval n = Random(0).nextInt()\n"},
            [entry],
        )

        self.assertEqual([v.line for v in violations], [2])

    def test_every_real_allowed_entry_has_a_reason(self) -> None:
        for entry in ALLOWED:
            self.assertTrue(entry.reason.strip(), entry.path)

    def test_stale_allowlist_entry_is_reported(self) -> None:
        entry = AllowedLine(re.escape(PREVIEW_FILE), r"Random\(0\)", "seeded")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write(root, PREVIEW_FILE, "@Preview\nfun P() {}\n")

            self.assertEqual(find_stale_entries(root, [entry]), [entry])

    def test_main_exits_one_on_a_violation_and_zero_when_clean(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            write(root, PREVIEW_FILE, "@Preview\nval n = Random.nextInt()\n")
            args = ["--root", str(root)]

            self.assertEqual(main(args, allowed=[]), 1)

            write(root, PREVIEW_FILE, "@Preview\nval n = 4\n")

            self.assertEqual(main(args, allowed=[]), 0)


if __name__ == "__main__":
    unittest.main()
