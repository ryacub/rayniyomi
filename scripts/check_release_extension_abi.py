#!/usr/bin/env python3
"""Verify that a release APK keeps the extension source API ABI."""

from __future__ import annotations

import argparse
import struct
import sys
import zipfile
from pathlib import Path


ACC_FINAL = 0x10
ACC_SYNTHETIC = 0x1000

# The members that extensions link against at run time.
#
# R8 rewrites the app but does not know that extensions exist, so it can change
# one of these and nothing fails at build time. A debug build cannot show it,
# because a debug build does not minify. #817 was exactly that: R8 made
# AnimeHttpSource.getFilterList() final, every extension override became illegal,
# and no source registered on release builds only.
#
# The curated lists below hold the members that #817 touched. The baseline holds
# the whole surface, so a member nobody remembered to list is covered as well.
EXPORTED_PREFIXES = (
    "Leu/kanade/tachiyomi/source/",
    "Leu/kanade/tachiyomi/animesource/",
    "Leu/kanade/tachiyomi/network/",
    "Leu/kanade/tachiyomi/util/",
)

FLAG_NAMES = (
    (0x1, "PUBLIC"),
    (0x2, "PRIVATE"),
    (0x4, "PROTECTED"),
    (0x8, "STATIC"),
    (0x10, "FINAL"),
    (0x20, "SYNCHRONIZED"),
    (0x100, "NATIVE"),
    (0x400, "ABSTRACT"),
    (0x1000, "SYNTHETIC"),
)

BASELINE_HEADER = """\
# Extension-facing ABI baseline. Generated file, do not edit by hand.
#
# Each line is one exported member with its access flags. A difference means an
# installed extension can break, because extensions were built against the
# previous surface. Access flags are recorded because #817 was a flag change: a
# method became final, which made every extension override illegal.
#
# Regenerate after an intended ABI change, in the same pull request:
#   ./gradlew assembleStableRelease
#   ./scripts/check_release_extension_abi.py \\
#       app/build/outputs/apk/stable/release/app-stable-arm64-v8a-release-unsigned.apk \\
#       --baseline app/extension-abi-baseline.txt --write-baseline
"""

REQUIRED_METHODS = {
    "manga source model": [
        "Leu/kanade/tachiyomi/source/model/SManga;->getUrl()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/source/model/SManga;->setUrl(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/source/model/SManga;->getTitle()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/source/model/SManga;->setTitle(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/source/model/SManga;->getArtist()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/source/model/SManga;->setArtist(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/source/model/SManga;->getAuthor()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/source/model/SManga;->setAuthor(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/source/model/SManga;->getDescription()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/source/model/SManga;->setDescription(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/source/model/SManga;->getGenre()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/source/model/SManga;->setGenre(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/source/model/SManga;->getStatus()I",
        "Leu/kanade/tachiyomi/source/model/SManga;->setStatus(I)V",
        "Leu/kanade/tachiyomi/source/model/SManga;->getThumbnail_url()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/source/model/SManga;->setThumbnail_url(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/source/model/SManga;->getUpdate_strategy()Leu/kanade/tachiyomi/source/model/UpdateStrategy;",
        "Leu/kanade/tachiyomi/source/model/SManga;->setUpdate_strategy(Leu/kanade/tachiyomi/source/model/UpdateStrategy;)V",
        "Leu/kanade/tachiyomi/source/model/SManga;->getInitialized()Z",
        "Leu/kanade/tachiyomi/source/model/SManga;->setInitialized(Z)V",
        "Leu/kanade/tachiyomi/source/model/SManga;->getMemo()Lkotlinx/serialization/json/JsonObject;",
        "Leu/kanade/tachiyomi/source/model/SManga$Companion;->create()Leu/kanade/tachiyomi/source/model/SManga;",
        "Leu/kanade/tachiyomi/source/model/SChapter;->getUrl()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/source/model/SChapter;->setUrl(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/source/model/SChapter;->getName()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/source/model/SChapter;->setName(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/source/model/SChapter;->getDate_upload()J",
        "Leu/kanade/tachiyomi/source/model/SChapter;->setDate_upload(J)V",
        "Leu/kanade/tachiyomi/source/model/SChapter;->getChapter_number()F",
        "Leu/kanade/tachiyomi/source/model/SChapter;->setChapter_number(F)V",
        "Leu/kanade/tachiyomi/source/model/SChapter;->getScanlator()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/source/model/SChapter;->setScanlator(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/source/model/SChapter$Companion;->create()Leu/kanade/tachiyomi/source/model/SChapter;",
    ],
    "anime source model": [
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getUrl()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setUrl(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getTitle()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setTitle(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getArtist()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setArtist(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getAuthor()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setAuthor(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getDescription()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setDescription(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getGenre()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setGenre(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getStatus()I",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setStatus(I)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getThumbnail_url()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setThumbnail_url(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getBackground_url()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setBackground_url(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getUpdate_strategy()Leu/kanade/tachiyomi/animesource/model/AnimeUpdateStrategy;",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setUpdate_strategy(Leu/kanade/tachiyomi/animesource/model/AnimeUpdateStrategy;)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getFetch_type()Leu/kanade/tachiyomi/animesource/model/FetchType;",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setFetch_type(Leu/kanade/tachiyomi/animesource/model/FetchType;)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getSeason_number()D",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setSeason_number(D)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->getInitialized()Z",
        "Leu/kanade/tachiyomi/animesource/model/SAnime;->setInitialized(Z)V",
        "Leu/kanade/tachiyomi/animesource/model/SAnime$Companion;->create()Leu/kanade/tachiyomi/animesource/model/SAnime;",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->getUrl()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->setUrl(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->getName()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->setName(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->getDate_upload()J",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->setDate_upload(J)V",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->getEpisode_number()F",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->setEpisode_number(F)V",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->getFillermark()Z",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->setFillermark(Z)V",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->getScanlator()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->setScanlator(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->getSummary()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->setSummary(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->getPreview_url()Ljava/lang/String;",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode;->setPreview_url(Ljava/lang/String;)V",
        "Leu/kanade/tachiyomi/animesource/model/SEpisode$Companion;->create()Leu/kanade/tachiyomi/animesource/model/SEpisode;",
    ],
    "jsoup source helpers": [
        "Leu/kanade/tachiyomi/util/JsoupExtensionsKt;->selectText(Lorg/jsoup/nodes/Element;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
        "Leu/kanade/tachiyomi/util/JsoupExtensionsKt;->selectText$default(Lorg/jsoup/nodes/Element;Ljava/lang/String;Ljava/lang/String;ILjava/lang/Object;)Ljava/lang/String;",
        "Leu/kanade/tachiyomi/util/JsoupExtensionsKt;->selectInt(Lorg/jsoup/nodes/Element;Ljava/lang/String;I)I",
        "Leu/kanade/tachiyomi/util/JsoupExtensionsKt;->selectInt$default(Lorg/jsoup/nodes/Element;Ljava/lang/String;IILjava/lang/Object;)I",
        "Leu/kanade/tachiyomi/util/JsoupExtensionsKt;->attrOrText(Lorg/jsoup/nodes/Element;Ljava/lang/String;)Ljava/lang/String;",
        "Leu/kanade/tachiyomi/util/JsoupExtensionsKt;->asJsoup(Lokhttp3/Response;Ljava/lang/String;)Lorg/jsoup/nodes/Document;",
        "Leu/kanade/tachiyomi/util/JsoupExtensionsKt;->asJsoup$default(Lokhttp3/Response;Ljava/lang/String;ILjava/lang/Object;)Lorg/jsoup/nodes/Document;",
    ],
}

REQUIRED_NON_FINAL_CLASSES = [
    "Leu/kanade/tachiyomi/source/online/HttpSource;",
    "Leu/kanade/tachiyomi/source/online/ParsedHttpSource;",
    "Leu/kanade/tachiyomi/animesource/online/AnimeHttpSource;",
    "Leu/kanade/tachiyomi/animesource/online/ParsedAnimeHttpSource;",
]

REQUIRED_NON_FINAL_METHOD_NAMES = {
    "Leu/kanade/tachiyomi/source/online/HttpSource;": [
        "headersBuilder",
        "toString",
        "getHomeUrl",
        "fetchPopularManga",
        "popularMangaRequest",
        "popularMangaParse",
        "fetchSearchManga",
        "searchMangaRequest",
        "searchMangaParse",
        "fetchLatestUpdates",
        "latestUpdatesRequest",
        "latestUpdatesParse",
        "fetchMangaDetails",
        "mangaDetailsRequest",
        "mangaDetailsParse",
        "fetchChapterList",
        "chapterListRequest",
        "chapterListParse",
        "fetchPageList",
        "pageListRequest",
        "pageListParse",
        "getImageUrl",
        "fetchImageUrl",
        "imageUrlRequest",
        "imageUrlParse",
        "getImage",
        "imageRequest",
        "getMangaUrl",
        "getChapterUrl",
        "prepareNewChapter",
        "getFilterList",
    ],
    "Leu/kanade/tachiyomi/source/online/ParsedHttpSource;": [
        "popularMangaParse",
        "popularMangaSelector",
        "popularMangaFromElement",
        "popularMangaNextPageSelector",
        "searchMangaParse",
        "searchMangaSelector",
        "searchMangaFromElement",
        "searchMangaNextPageSelector",
        "latestUpdatesParse",
        "latestUpdatesSelector",
        "latestUpdatesFromElement",
        "latestUpdatesNextPageSelector",
        "mangaDetailsParse",
        "chapterListParse",
        "chapterListSelector",
        "chapterFromElement",
        "pageListParse",
        "imageUrlParse",
    ],
    "Leu/kanade/tachiyomi/animesource/online/AnimeHttpSource;": [
        "headersBuilder",
        "toString",
        "fetchPopularAnime",
        "popularAnimeRequest",
        "popularAnimeParse",
        "fetchSearchAnime",
        "searchAnimeRequest",
        "searchAnimeParse",
        "fetchLatestUpdates",
        "latestUpdatesRequest",
        "latestUpdatesParse",
        "getAnimeDetails",
        "fetchAnimeDetails",
        "animeDetailsRequest",
        "animeDetailsParse",
        "getEpisodeList",
        "fetchEpisodeList",
        "episodeListRequest",
        "episodeListParse",
        "episodeVideoParse",
        "getSeasonList",
        "seasonListRequest",
        "seasonListParse",
        "getHosterList",
        "hosterListRequest",
        "hosterListParse",
        "getVideoList",
        "videoListRequest",
        "videoListParse",
        "resolveVideo",
        "fetchVideoList",
        "sortHosters",
        "sortVideos",
        "sort",
        "getVideoUrl",
        "fetchVideoUrl",
        "videoUrlRequest",
        "videoUrlParse",
        "getAnimeUrl",
        "getEpisodeUrl",
        "prepareNewEpisode",
        "getFilterList",
    ],
    "Leu/kanade/tachiyomi/animesource/online/ParsedAnimeHttpSource;": [
        "popularAnimeParse",
        "popularAnimeSelector",
        "popularAnimeFromElement",
        "popularAnimeNextPageSelector",
        "searchAnimeParse",
        "searchAnimeSelector",
        "searchAnimeFromElement",
        "searchAnimeNextPageSelector",
        "latestUpdatesParse",
        "latestUpdatesSelector",
        "latestUpdatesFromElement",
        "latestUpdatesNextPageSelector",
        "animeDetailsParse",
        "episodeListParse",
        "episodeListSelector",
        "episodeFromElement",
        "seasonListParse",
        "seasonListSelector",
        "seasonFromElement",
        "hosterListParse",
        "hosterListSelector",
        "hosterFromElement",
        "videoListParse",
        "videoListSelector",
        "videoFromElement",
        "videoUrlParse",
    ],
}


def read_u16(data: bytes, offset: int) -> int:
    return struct.unpack_from("<H", data, offset)[0]


def read_u32(data: bytes, offset: int) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def read_uleb128(data: bytes, offset: int) -> tuple[int, int]:
    result = 0
    shift = 0
    current = offset
    while True:
        byte = data[current]
        current += 1
        result |= (byte & 0x7F) << shift
        if (byte & 0x80) == 0:
            return result, current
        shift += 7


def read_string(data: bytes, offset: int) -> str:
    _, current = read_uleb128(data, offset)
    end = current
    while data[end] != 0:
        end += 1
    return data[current:end].decode("utf-8")


class DexFile:
    def __init__(self, data: bytes, name: str) -> None:
        if not data.startswith(b"dex\n"):
            raise ValueError(f"{name} is not a DEX file")
        self.data = data
        self.name = name
        self.string_ids_size = read_u32(data, 56)
        self.string_ids_off = read_u32(data, 60)
        self.type_ids_size = read_u32(data, 64)
        self.type_ids_off = read_u32(data, 68)
        self.proto_ids_size = read_u32(data, 72)
        self.proto_ids_off = read_u32(data, 76)
        self.method_ids_size = read_u32(data, 88)
        self.method_ids_off = read_u32(data, 92)
        self.class_defs_size = read_u32(data, 96)
        self.class_defs_off = read_u32(data, 100)
        self._strings: dict[int, str] = {}
        self._types: dict[int, str] = {}
        self._protos: dict[int, str] = {}

    def string(self, index: int) -> str:
        if index not in self._strings:
            string_data_off = read_u32(self.data, self.string_ids_off + index * 4)
            self._strings[index] = read_string(self.data, string_data_off)
        return self._strings[index]

    def type_descriptor(self, index: int) -> str:
        if index not in self._types:
            descriptor_idx = read_u32(self.data, self.type_ids_off + index * 4)
            self._types[index] = self.string(descriptor_idx)
        return self._types[index]

    def proto_descriptor(self, index: int) -> str:
        if index not in self._protos:
            proto_off = self.proto_ids_off + index * 12
            return_type_idx = read_u32(self.data, proto_off + 4)
            parameters_off = read_u32(self.data, proto_off + 8)
            parameters = ""
            if parameters_off != 0:
                size = read_u32(self.data, parameters_off)
                param_off = parameters_off + 4
                parameters = "".join(
                    self.type_descriptor(read_u16(self.data, param_off + i * 2))
                    for i in range(size)
                )
            self._protos[index] = f"({parameters}){self.type_descriptor(return_type_idx)}"
        return self._protos[index]

    def method_signature(self, index: int) -> str:
        method_off = self.method_ids_off + index * 8
        class_idx = read_u16(self.data, method_off)
        proto_idx = read_u16(self.data, method_off + 2)
        name_idx = read_u32(self.data, method_off + 4)
        return f"{self.type_descriptor(class_idx)}->{self.string(name_idx)}{self.proto_descriptor(proto_idx)}"

    def defined_methods(self) -> set[str]:
        methods: set[str] = set()
        _, method_flags = self.access_flags()
        methods.update(method_flags)
        return methods

    def access_flags(self) -> tuple[dict[str, int], dict[str, int]]:
        class_flags: dict[str, int] = {}
        method_flags: dict[str, int] = {}
        for class_index in range(self.class_defs_size):
            class_def_off = self.class_defs_off + class_index * 32
            class_idx = read_u32(self.data, class_def_off)
            class_flags[self.type_descriptor(class_idx)] = read_u32(self.data, class_def_off + 4)
            class_data_off = read_u32(self.data, class_def_off + 24)
            if class_data_off == 0:
                continue
            current = class_data_off
            static_fields_size, current = read_uleb128(self.data, current)
            instance_fields_size, current = read_uleb128(self.data, current)
            direct_methods_size, current = read_uleb128(self.data, current)
            virtual_methods_size, current = read_uleb128(self.data, current)
            for _ in range(static_fields_size + instance_fields_size):
                _, current = read_uleb128(self.data, current)
                _, current = read_uleb128(self.data, current)
            for method_count in (direct_methods_size, virtual_methods_size):
                method_index = 0
                for _ in range(method_count):
                    method_index_diff, current = read_uleb128(self.data, current)
                    method_index += method_index_diff
                    flags, current = read_uleb128(self.data, current)
                    _, current = read_uleb128(self.data, current)
                    method_flags[self.method_signature(method_index)] = flags
        return class_flags, method_flags


def dex_entries(path: Path) -> list[tuple[str, bytes]]:
    if zipfile.is_zipfile(path):
        with zipfile.ZipFile(path) as apk:
            return [
                (name, apk.read(name))
                for name in sorted(apk.namelist())
                if name.startswith("classes") and name.endswith(".dex")
            ]
    return [(path.name, path.read_bytes())]


def collect_defined_methods(path: Path) -> set[str]:
    entries = dex_entries(path)
    if not entries:
        raise ValueError(f"{path} does not contain DEX files")
    methods: set[str] = set()
    for name, data in entries:
        methods.update(DexFile(data, name).defined_methods())
    return methods


def collect_access_flags(path: Path) -> tuple[dict[str, int], dict[str, int]]:
    entries = dex_entries(path)
    if not entries:
        raise ValueError(f"{path} does not contain DEX files")
    class_flags: dict[str, int] = {}
    method_flags: dict[str, int] = {}
    for name, data in entries:
        dex_class_flags, dex_method_flags = DexFile(data, name).access_flags()
        class_flags.update(dex_class_flags)
        method_flags.update(dex_method_flags)
    return class_flags, method_flags


def method_name(signature: str) -> str:
    return signature.split("->", 1)[1].split("(", 1)[0]


def non_final_method_violations(
    method_flags: dict[str, int],
) -> tuple[dict[str, list[str]], dict[str, list[str]]]:
    missing_by_class: dict[str, list[str]] = {}
    final_by_class: dict[str, list[str]] = {}

    for class_name, method_names in REQUIRED_NON_FINAL_METHOD_NAMES.items():
        class_methods: dict[str, list[tuple[str, int]]] = {}
        for signature, flags in method_flags.items():
            if signature.startswith(f"{class_name}->"):
                class_methods.setdefault(method_name(signature), []).append((signature, flags))
        for name in method_names:
            matches = class_methods.get(name)
            if matches is None:
                missing_by_class.setdefault(class_name, []).append(name)
                continue
            for signature, flags in matches:
                if flags & ACC_FINAL:
                    final_by_class.setdefault(class_name, []).append(signature)

    return missing_by_class, final_by_class


def render_flags(flags: int) -> str:
    names = [name for bit, name in FLAG_NAMES if flags & bit]
    return " ".join(names) if names else "NONE"


def is_synthetic_signature(signature: str, flags: int) -> bool:
    """Report whether a member is a compiler artifact rather than exported API.

    Two forms exist and only one carries ACC_SYNTHETIC. R8 also emits whole
    classes, such as `LocaleHelper$$ExternalSyntheticLambda0`, whose members are
    ordinary public interface methods with no synthetic flag. Those class names
    hold an index that R8 assigns per class, so an unrelated change elsewhere
    renumbers or drops one and fails the check for no reason. Match them by name.

    Kotlin's own `$$serializer` and `$$inlined` classes are named by the compiler
    from the source, so they stay stable and remain part of the surface.
    """
    if flags & ACC_SYNTHETIC:
        return True
    class_name = signature.split(";->", 1)[0]
    return "$$ExternalSynthetic" in class_name


def baseline_rows(method_flags: dict[str, int]) -> list[str]:
    """Return the exported surface as stable, sorted lines.

    Synthetic members are left out. R8 generates their names, for example
    `access$fetchChapterList$jd`, so they are not stable between builds and would
    make the check fail for no reason. See [is_synthetic_signature].
    """
    return sorted(
        f"{signature} [{render_flags(flags)}]"
        for signature, flags in method_flags.items()
        if signature.startswith(EXPORTED_PREFIXES)
        and not is_synthetic_signature(signature, flags)
    )


def read_baseline(text: str) -> set[str]:
    return {
        line.strip()
        for line in text.splitlines()
        if line.strip() and not line.startswith("#")
    }


def baseline_differences(
    rows: list[str],
    baseline_text: str,
) -> tuple[list[str], list[str]]:
    """Return the members that the baseline has and the build lost, then the new ones."""
    current = set(rows)
    recorded = read_baseline(baseline_text)
    return sorted(recorded - current), sorted(current - recorded)


def check_abi(path: Path, baseline: Path | None = None) -> int:
    methods = collect_defined_methods(path)
    class_flags, method_flags = collect_access_flags(path)
    missing_by_group = {
        group: [method for method in required if method not in methods]
        for group, required in REQUIRED_METHODS.items()
    }
    missing_by_group = {
        group: methods
        for group, methods in missing_by_group.items()
        if methods
    }

    missing_classes = [
        class_name
        for class_name in REQUIRED_NON_FINAL_CLASSES
        if class_name not in class_flags
    ]
    final_classes = [
        class_name
        for class_name in REQUIRED_NON_FINAL_CLASSES
        if class_flags.get(class_name, 0) & ACC_FINAL
    ]
    missing_non_final_methods, final_methods = non_final_method_violations(method_flags)

    rows = baseline_rows(method_flags)
    lost: list[str] = []
    added: list[str] = []
    if baseline is not None:
        lost, added = baseline_differences(rows, baseline.read_text(encoding="utf-8"))

    if (
        not missing_by_group
        and not missing_classes
        and not final_classes
        and not missing_non_final_methods
        and not final_methods
        and not lost
        and not added
    ):
        required_count = sum(len(methods) for methods in REQUIRED_METHODS.values())
        non_final_count = sum(
            len(methods)
            for methods in REQUIRED_NON_FINAL_METHOD_NAMES.values()
        )
        print(
            f"OK: {path} defines {required_count} required extension ABI methods "
            f"and keeps {non_final_count} extension-open methods non-final.",
        )
        if baseline is not None:
            print(f"OK: the exported surface matches {baseline} ({len(rows)} members).")
        return 0

    print(f"ERROR: {path} does not preserve the required extension ABI.")
    if missing_by_group:
        for group, missing in missing_by_group.items():
            print(f"\n{group}:")
            for method in missing:
                print(f"  - {method}")
    if missing_classes:
        print("\nextension-open classes missing:")
        for class_name in missing_classes:
            print(f"  - {class_name}")
    if final_classes:
        print("\nextension-open classes finalized:")
        for class_name in final_classes:
            print(f"  - {class_name}")
    if missing_non_final_methods:
        print("\nextension-open methods missing:")
        for class_name, method_names in missing_non_final_methods.items():
            print(f"  {class_name}:")
            for name in method_names:
                print(f"    - {name}")
    if final_methods:
        print("\nextension-open methods finalized with ACC_FINAL:")
        for class_name, signatures in final_methods.items():
            print(f"  {class_name}:")
            for signature in signatures:
                print(f"    - {signature}")
    if lost or added:
        print(
            "\nThe exported surface no longer matches the baseline. An installed "
            "extension can break.",
        )
        for signature in lost:
            print(f"  - {signature}")
        for signature in added:
            print(f"  + {signature}")
        print(
            "\nIf the change is intended, regenerate the baseline in this pull "
            "request with --write-baseline.",
        )
    return 1


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify required source API methods in an optimized Rayniyomi APK.",
    )
    parser.add_argument("apk", type=Path, help="APK or DEX file to inspect")
    parser.add_argument(
        "--baseline",
        type=Path,
        help="Baseline of the exported surface to compare against",
    )
    parser.add_argument(
        "--write-baseline",
        action="store_true",
        help="Write the baseline from this APK instead of checking it",
    )
    args = parser.parse_args()

    if not args.apk.is_file():
        print(f"ERROR: file does not exist: {args.apk}", file=sys.stderr)
        return 2

    if args.write_baseline and args.baseline is None:
        print("ERROR: --write-baseline needs --baseline", file=sys.stderr)
        return 2
    if args.baseline is not None and not args.write_baseline and not args.baseline.is_file():
        print(f"ERROR: baseline does not exist: {args.baseline}", file=sys.stderr)
        return 2

    try:
        if args.write_baseline:
            _, method_flags = collect_access_flags(args.apk)
            rows = baseline_rows(method_flags)
            args.baseline.write_text(
                BASELINE_HEADER + "\n".join(rows) + "\n",
                encoding="utf-8",
            )
            print(f"OK: wrote {len(rows)} exported members to {args.baseline}")
            return 0
        return check_abi(args.apk, args.baseline)
    except (OSError, ValueError, struct.error, IndexError) as error:
        print(f"ERROR: cannot inspect {args.apk}: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
