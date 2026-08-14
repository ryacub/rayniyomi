from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from scripts.check_source_gateway import find_direct_calls


class SourceGatewayCheckTest(unittest.TestCase):
    def test_rejects_a_direct_source_call(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/example/DirectCall.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                """\
package example

import eu.kanade.tachiyomi.source.CatalogueSource

suspend fun load(source: CatalogueSource) = source.getPopularManga(1)
""",
                encoding="utf-8",
            )

            violations = find_direct_calls(root)

            self.assertEqual(len(violations), 1)
            self.assertEqual(violations[0].method, "getPopularManga")

    def test_accepts_the_named_gateway(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/example/GatewayCall.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                """\
package example

import eu.kanade.tachiyomi.source.CatalogueSource
import tachiyomi.data.source.manga.MangaSourceGateway

suspend fun load(source: CatalogueSource) = MangaSourceGateway.popular(source, 1)
""",
                encoding="utf-8",
            )

            self.assertEqual(find_direct_calls(root), [])

    def test_ignores_a_named_non_source_method(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/eu/kanade/tachiyomi/data/track/myanimelist/MyAnimeList.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                """\
package example

suspend fun load(api: TrackerApi) = api.getMangaDetails(1)
""",
                encoding="utf-8",
            )

            self.assertEqual(find_direct_calls(root), [])

    def test_rejects_an_unknown_method_name_collision(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/example/Tracker.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                """\
package example

suspend fun load(api: TrackerApi) = api.getMangaDetails(1)
""",
                encoding="utf-8",
            )

            self.assertEqual(len(find_direct_calls(root)), 1)

    def test_rejects_a_receiver_that_only_looks_like_a_gateway(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/example/Lookalike.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                """\
package example

suspend fun load(fakeSourceGateway: CatalogueSource) = fakeSourceGateway.getPopularManga(1)
""",
                encoding="utf-8",
            )

            self.assertEqual(len(find_direct_calls(root)), 1)

    def test_rejects_a_direct_call_after_a_cast(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/example/CastCall.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                """\
package example

suspend fun load(source: MangaSource) = (source as CatalogueSource).getPopularManga(1)
""",
                encoding="utf-8",
            )

            self.assertEqual(len(find_direct_calls(root)), 1)

    def test_rejects_a_source_method_reference(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/example/Reference.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                """\
package example

fun load(source: MangaSource) = source::getPageList
""",
                encoding="utf-8",
            )

            self.assertEqual(len(find_direct_calls(root)), 1)

    def test_rejects_an_unqualified_scope_call(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "app/src/main/java/example/Scope.kt"
            source.parent.mkdir(parents=True)
            source.write_text(
                """\
package example

suspend fun load(source: MangaSource, chapter: SChapter) = source.run { getPageList(chapter) }
""",
                encoding="utf-8",
            )

            self.assertEqual(len(find_direct_calls(root)), 1)

    def test_current_host_sources_use_the_gateway(self) -> None:
        repository = Path(__file__).resolve().parents[2]

        self.assertEqual(find_direct_calls(repository), [])


if __name__ == "__main__":
    unittest.main()
