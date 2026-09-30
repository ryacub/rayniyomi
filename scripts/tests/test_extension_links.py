from __future__ import annotations

import unittest

from scripts.check_extension_links import HostSurface, unresolved_references

SOURCE = "Leu/kanade/tachiyomi/source/Source;"
MANGA_SOURCE = "Leu/kanade/tachiyomi/source/MangaSource;"
CATALOGUE_SOURCE = "Leu/kanade/tachiyomi/source/CatalogueSource;"
HTTP_SOURCE = "Leu/kanade/tachiyomi/source/online/HttpSource;"
HTTP_EXCEPTION = "Leu/kanade/tachiyomi/network/HttpException;"
EXTENSION = "Lkeiyoushi/source/Generated;"


def host() -> HostSurface:
    return HostSurface(
        members={
            SOURCE: {"getSupportsLatest()Z"},
            MANGA_SOURCE: {"getId()J"},
            CATALOGUE_SOURCE: set(),
            HTTP_SOURCE: {"getBaseUrl()Ljava/lang/String;", "client:Lokhttp3/OkHttpClient;"},
            HTTP_EXCEPTION: {"code:I"},
        },
        parents={
            SOURCE: ["Ljava/lang/Object;"],
            MANGA_SOURCE: ["Ljava/lang/Object;", SOURCE],
            CATALOGUE_SOURCE: ["Ljava/lang/Object;", MANGA_SOURCE],
            HTTP_SOURCE: ["Ljava/lang/Object;", CATALOGUE_SOURCE],
            HTTP_EXCEPTION: ["Ljava/lang/IllegalStateException;"],
        },
    )


class ExtensionLinksTest(unittest.TestCase):
    def test_resolves_a_member_on_the_referenced_class(self) -> None:
        refs = {(HTTP_SOURCE, "getBaseUrl()Ljava/lang/String;"), (HTTP_SOURCE, "client:Lokhttp3/OkHttpClient;")}

        self.assertEqual(unresolved_references(refs, host(), own_classes=set()), [])

    def test_resolves_a_member_through_superclasses_and_interfaces(self) -> None:
        """The lib-1.6 call: `invoke-interface Source.getSupportsLatest` reached from HttpSource."""
        refs = {(HTTP_SOURCE, "getSupportsLatest()Z"), (CATALOGUE_SOURCE, "getId()J")}

        self.assertEqual(unresolved_references(refs, host(), own_classes=set()), [])

    def test_reports_a_member_that_the_class_does_not_define(self) -> None:
        """R8 removed HttpException.getCode() and kept only the field."""
        refs = {(HTTP_EXCEPTION, "getCode()I")}

        self.assertEqual(
            unresolved_references(refs, host(), own_classes=set()),
            [(HTTP_EXCEPTION, "getCode()I")],
        )

    def test_reports_a_member_of_an_absent_class(self) -> None:
        refs = {("Leu/kanade/tachiyomi/source/Missing;", "getSupportsLatest()Z")}

        self.assertEqual(
            unresolved_references(refs, host(), own_classes=set()),
            [("Leu/kanade/tachiyomi/source/Missing;", "getSupportsLatest()Z")],
        )

    def test_reports_a_member_that_only_a_platform_ancestor_could_define(self) -> None:
        """The host dex has no platform classes, so the script cannot prove these.

        Leniency here would hide HttpException.getCode(), because HttpException
        extends a platform class. A reviewer reads the rare false report instead.
        """
        refs = {(HTTP_EXCEPTION, "getMessage()Ljava/lang/String;")}

        self.assertEqual(
            unresolved_references(refs, host(), own_classes=set()),
            [(HTTP_EXCEPTION, "getMessage()Ljava/lang/String;")],
        )

    def test_ignores_extension_classes_and_platform_classes(self) -> None:
        refs = {
            (EXTENSION, "getClient()Lokhttp3/OkHttpClient;"),
            ("Landroid/net/Uri;", "parse(Ljava/lang/String;)Landroid/net/Uri;"),
            ("Ljava/util/ArrayList;", "add(Ljava/lang/Object;)Z"),
            ("[C", "clone()Ljava/lang/Object;"),
            ("[Lkeiyoushi/source/Generated;", "clone()Ljava/lang/Object;"),
        }

        self.assertEqual(unresolved_references(refs, host(), own_classes={EXTENSION}), [])

    def test_does_not_loop_on_a_cyclic_hierarchy(self) -> None:
        surface = HostSurface(
            members={"La;": set(), "Lb;": set()},
            parents={"La;": ["Lb;"], "Lb;": ["La;"]},
        )

        self.assertEqual(
            unresolved_references({("La;", "x()V")}, surface, own_classes=set()),
            [("La;", "x()V")],
        )


if __name__ == "__main__":
    unittest.main()
