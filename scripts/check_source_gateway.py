#!/usr/bin/env python3
"""Reject direct host calls into the manga and anime extension source APIs."""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path


SOURCE_ROOTS = (
    Path("app/src/main/java"),
    Path("data/src/main/java"),
)

GATEWAY_FILES = {
    Path("data/src/main/java/tachiyomi/data/source/manga/MangaSourceGateway.kt"),
    Path("data/src/main/java/tachiyomi/data/source/anime/AnimeSourceGateway.kt"),
}

ENTRY_POINTS = {
    "getPopularManga",
    "getSearchManga",
    "getLatestUpdates",
    "getMangaDetails",
    "getChapterList",
    "getPageList",
    "getFilterList",
    "getImageUrl",
    "getImage",
    "getMangaUrl",
    "getChapterUrl",
    "prepareNewChapter",
    "getUriType",
    "getManga",
    "getChapter",
    "setupPreferenceScreen",
    "getPopularAnime",
    "getSearchAnime",
    "getAnimeDetails",
    "getEpisodeList",
    "getSeasonList",
    "getHosterList",
    "getVideoList",
    "resolveVideo",
    "sortHosters",
    "sortVideos",
    "getVideoUrl",
    "getAnimeUrl",
    "getEpisodeUrl",
    "prepareNewEpisode",
    "getAnime",
    "getEpisode",
}

# These methods share source API names but run on host-owned screen models or tracker APIs.
# Keep each exception exact. A new collision must fail until this list records its receiver.
NON_SOURCE_CALLS = {
    (
        Path("app/src/main/java/eu/kanade/tachiyomi/data/track/myanimelist/MyAnimeList.kt"),
        "api",
        "getMangaDetails",
    ),
    (
        Path("app/src/main/java/eu/kanade/tachiyomi/data/track/myanimelist/MyAnimeList.kt"),
        "api",
        "getAnimeDetails",
    ),
    (
        Path("app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/migration/search/MigrateAnimeSearchScreen.kt"),
        "screenModel",
        "getAnime",
    ),
    (
        Path("app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/source/globalsearch/GlobalAnimeSearchScreen.kt"),
        "screenModel",
        "getAnime",
    ),
    (
        Path("app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/migration/search/MigrateMangaSearchScreen.kt"),
        "screenModel",
        "getManga",
    ),
    (
        Path("app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/source/globalsearch/GlobalMangaSearchScreen.kt"),
        "screenModel",
        "getManga",
    ),
}

# These unqualified calls and method references target host-owned functions.
# Match the whole normalized line so a new use fails even when it shares a method name.
NON_SOURCE_LINES = {
    (Path("app/src/main/java/eu/kanade/presentation/browse/anime/components/GlobalAnimeSearchCardRow.kt"), "getAnime", "val title by getAnime(it)"),
    (Path("app/src/main/java/eu/kanade/presentation/browse/manga/components/GlobalMangaSearchCardRow.kt"), "getManga", "val title by getManga(it)"),
    (Path("app/src/main/java/eu/kanade/presentation/entries/anime/EpisodeOptionsDialogScreen.kt"), "getHosterList", "getHosterList = sm::getHosterList,"),
    (Path("app/src/main/java/eu/kanade/presentation/entries/anime/EpisodeOptionsDialogScreen.kt"), "getHosterList", "getHosterList(),"),
    (Path("app/src/main/java/eu/kanade/tachiyomi/data/track/myanimelist/MyAnimeListApi.kt"), "getMangaDetails", ".map { async { getMangaDetails(it.node.id) } }"),
    (Path("app/src/main/java/eu/kanade/tachiyomi/data/track/myanimelist/MyAnimeListApi.kt"), "getAnimeDetails", ".map { async { getAnimeDetails(it.node.id) } }"),
    (Path("app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt"), "getChapterUrl", "val url = getChapterUrl()"),
    (Path("app/src/main/java/eu/kanade/tachiyomi/ui/player/ExternalIntents.kt"), "getVideoUrl", "val videoUrl = getVideoUrl(source, context, video) ?: return null"),
    (Path("app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/MangaScreen.kt"), "getMangaUrl", "assistUrl = getMangaUrl(screenModel.manga, screenModel.source)"),
    (Path("app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/MangaScreen.kt"), "getMangaUrl", "getMangaUrl(manga_, source_)?.let { url ->"),
    (Path("app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreen.kt"), "getAnimeUrl", "assistUrl = getAnimeUrl(screenModel.anime, screenModel.source)"),
    (Path("app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreen.kt"), "getAnimeUrl", "getAnimeUrl(anime_, source_)?.let { url ->"),
}

GATEWAY_RECEIVERS = {"MangaSourceGateway", "AnimeSourceGateway"}

ENTRY_POINT_PATTERN = "|".join(sorted(ENTRY_POINTS))
ENTRY_POINT_USE = re.compile(
    rf"(?:::)(?P<reference>{ENTRY_POINT_PATTERN})\b|(?P<call>{ENTRY_POINT_PATTERN})\s*\(",
)
RECEIVER = re.compile(
    r"(?P<receiver>[A-Za-z_][A-Za-z0-9_]*(?:\s*\.\s*[A-Za-z_][A-Za-z0-9_]*)*)"
    r"\s*(?:\.|\?\.)\s*$",
)


@dataclass(frozen=True)
class Violation:
    path: Path
    line: int
    method: str


def _strip_comments_and_literals(text: str) -> str:
    output = list(text)
    index = 0
    block_depth = 0
    quote: str | None = None
    escaped = False

    while index < len(text):
        if block_depth:
            if text.startswith("/*", index):
                output[index : index + 2] = "  "
                block_depth += 1
                index += 2
            elif text.startswith("*/", index):
                output[index : index + 2] = "  "
                block_depth -= 1
                index += 2
            else:
                if text[index] != "\n":
                    output[index] = " "
                index += 1
            continue

        if quote is not None:
            if quote == '"""' and text.startswith('"""', index):
                output[index : index + 3] = "   "
                quote = None
                index += 3
            elif quote != '"""' and not escaped and text[index] == quote:
                output[index] = " "
                quote = None
                index += 1
            else:
                escaped = not escaped and text[index] == "\\"
                if text[index] != "\n":
                    output[index] = " "
                index += 1
            continue

        if text.startswith("//", index):
            end = text.find("\n", index)
            end = len(text) if end == -1 else end
            output[index:end] = " " * (end - index)
            index = end
        elif text.startswith("/*", index):
            output[index : index + 2] = "  "
            block_depth = 1
            index += 2
        elif text.startswith('"""', index):
            output[index : index + 3] = "   "
            quote = '"""'
            index += 3
        elif text[index] in {'"', "'"}:
            quote = text[index]
            escaped = False
            output[index] = " "
            index += 1
        else:
            index += 1

    return "".join(output)


def find_direct_calls(repository: Path) -> list[Violation]:
    violations: list[Violation] = []
    for source_root in SOURCE_ROOTS:
        absolute_root = repository / source_root
        if not absolute_root.exists():
            continue
        for path in absolute_root.rglob("*.kt"):
            relative_path = path.relative_to(repository)
            if relative_path in GATEWAY_FILES:
                continue
            text = path.read_text(encoding="utf-8")
            lines = text.splitlines()
            searchable = _strip_comments_and_literals(text)
            for match in ENTRY_POINT_USE.finditer(searchable):
                method = match.group("reference") or match.group("call")
                prefix = searchable[: match.start()]
                if match.group("call") and re.search(r"\bfun\s*$", prefix):
                    continue
                line = searchable.count("\n", 0, match.start()) + 1
                normalized_line = lines[line - 1].strip()
                receiver_match = RECEIVER.search(prefix)
                receiver = re.sub(r"\s+", "", receiver_match.group("receiver")) if receiver_match else ""
                if receiver in GATEWAY_RECEIVERS:
                    continue
                if (relative_path, receiver, method) in NON_SOURCE_CALLS:
                    continue
                if (relative_path, method, normalized_line) in NON_SOURCE_LINES:
                    continue
                violations.append(
                    Violation(
                        path=relative_path,
                        line=line,
                        method=method,
                    ),
                )
    return violations


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()

    violations = find_direct_calls(args.root.resolve())
    if not violations:
        print("Source gateway check passed.")
        return 0

    print("Direct extension source calls must use MangaSourceGateway or AnimeSourceGateway:", file=sys.stderr)
    for violation in violations:
        print(f"  {violation.path}:{violation.line}: {violation.method}", file=sys.stderr)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
