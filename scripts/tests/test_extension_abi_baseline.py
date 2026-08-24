from __future__ import annotations

import unittest

from scripts.check_release_extension_abi import (
    ACC_FINAL,
    ACC_SYNTHETIC,
    REQUIRED_METHODS,
    baseline_differences,
    baseline_rows,
    render_flags,
)

ACC_PUBLIC = 0x1
ACC_STATIC = 0x8

GET_FILTER_LIST = (
    "Leu/kanade/tachiyomi/animesource/online/AnimeHttpSource;"
    "->getFilterList()Leu/kanade/tachiyomi/animesource/model/AnimeFilterList;"
)


class ExtensionAbiBaselineTest(unittest.TestCase):
    def test_records_exported_members_with_flags(self) -> None:
        rows = baseline_rows({GET_FILTER_LIST: ACC_PUBLIC})

        self.assertEqual(rows, [f"{GET_FILTER_LIST} [PUBLIC]"])

    def test_ignores_members_outside_the_exported_packages(self) -> None:
        rows = baseline_rows(
            {
                GET_FILTER_LIST: ACC_PUBLIC,
                "Lokhttp3/OkHttpClient;->newCall(Lokhttp3/Request;)Lokhttp3/Call;": ACC_PUBLIC,
                "Ltachiyomi/data/Foo;->bar()V": ACC_PUBLIC,
            },
        )

        self.assertEqual(rows, [f"{GET_FILTER_LIST} [PUBLIC]"])

    def test_ignores_synthetic_members(self) -> None:
        """R8 names them, for example `access$fetchChapterList$jd`, so they are not stable."""
        rows = baseline_rows(
            {
                "Leu/kanade/tachiyomi/source/CatalogueSource;->access$fetch$jd()V": (
                    ACC_PUBLIC | ACC_STATIC | ACC_SYNTHETIC
                ),
            },
        )

        self.assertEqual(rows, [])

    def test_ignores_members_of_r8_synthesized_classes(self) -> None:
        """The class is R8's, so the index moves when unrelated code changes.

        The method itself is an ordinary public interface method and carries no
        ACC_SYNTHETIC, so only the class name identifies it.
        """
        rows = baseline_rows(
            {
                "Leu/kanade/tachiyomi/util/system/LocaleHelper$$ExternalSyntheticLambda0;"
                "->invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;": (
                    ACC_PUBLIC | ACC_FINAL
                ),
            },
        )

        self.assertEqual(rows, [])

    def test_keeps_kotlin_compiler_generated_classes(self) -> None:
        """`$$serializer` and `$$inlined` names come from the source, so they are stable."""
        serializer = (
            "Leu/kanade/tachiyomi/source/model/Page$$serializer;"
            "->serialize(Lkotlinx/serialization/encoding/Encoder;)V"
        )
        rows = baseline_rows({serializer: ACC_PUBLIC})

        self.assertEqual(rows, [f"{serializer} [PUBLIC]"])

    def test_a_matching_build_reports_no_difference(self) -> None:
        rows = baseline_rows({GET_FILTER_LIST: ACC_PUBLIC})

        lost, added = baseline_differences(rows, "\n".join(rows))

        self.assertEqual((lost, added), ([], []))

    def test_detects_a_finalized_method(self) -> None:
        """This is #817: R8 made getFilterList final, so every extension override died."""
        baseline = f"{GET_FILTER_LIST} [PUBLIC]"
        rows = baseline_rows({GET_FILTER_LIST: ACC_PUBLIC | ACC_FINAL})

        lost, added = baseline_differences(rows, baseline)

        self.assertEqual(lost, [f"{GET_FILTER_LIST} [PUBLIC]"])
        self.assertEqual(added, [f"{GET_FILTER_LIST} [PUBLIC FINAL]"])

    def test_detects_a_removed_method(self) -> None:
        lost, added = baseline_differences([], f"{GET_FILTER_LIST} [PUBLIC]")

        self.assertEqual(lost, [f"{GET_FILTER_LIST} [PUBLIC]"])
        self.assertEqual(added, [])

    def test_baseline_comments_are_not_compared(self) -> None:
        rows = baseline_rows({GET_FILTER_LIST: ACC_PUBLIC})
        baseline = "# generated file\n#\n" + "\n".join(rows) + "\n"

        lost, added = baseline_differences(rows, baseline)

        self.assertEqual((lost, added), ([], []))

    def test_render_flags_is_ordered_and_stable(self) -> None:
        self.assertEqual(render_flags(ACC_PUBLIC | ACC_FINAL), "PUBLIC FINAL")
        self.assertEqual(render_flags(ACC_FINAL | ACC_PUBLIC), "PUBLIC FINAL")
        self.assertEqual(render_flags(0), "NONE")

    def test_requires_manga_get_memo(self) -> None:
        """R917: the gate names SManga.getMemo so its removal fails with a reason."""
        self.assertIn(
            "Leu/kanade/tachiyomi/source/model/SManga;"
            "->getMemo()Lkotlinx/serialization/json/JsonObject;",
            REQUIRED_METHODS["manga source model"],
        )


if __name__ == "__main__":
    unittest.main()

