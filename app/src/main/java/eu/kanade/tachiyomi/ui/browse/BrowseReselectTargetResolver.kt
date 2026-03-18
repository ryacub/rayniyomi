package eu.kanade.tachiyomi.ui.browse

import java.util.concurrent.atomic.AtomicReference

internal enum class BrowseSearchTarget {
    ANIME,
    MANGA,
    UNKNOWN,
}

internal class BrowseReselectTargetResolver(
    initialSearchTarget: BrowseSearchTarget = BrowseSearchTarget.UNKNOWN,
) {
    private val lastKnownSearchTarget = AtomicReference(initialSearchTarget)

    fun updateForPage(page: Int) {
        lastKnownSearchTarget.updateAndGet { current ->
            updateBrowseSearchTarget(current, page)
        }
    }

    fun resolvedTarget(): BrowseSearchTarget {
        return resolveBrowseReselectTarget(lastKnownSearchTarget.get())
    }
}

private const val ANIME_SOURCES_PAGE = 0
private const val MANGA_SOURCES_PAGE = 1
private const val ANIME_EXTENSIONS_PAGE = 2
private const val MANGA_EXTENSIONS_PAGE = 3
private const val ANIME_MIGRATION_PAGE = 4
private const val MANGA_MIGRATION_PAGE = 5

internal fun browseSearchTargetForPage(page: Int): BrowseSearchTarget {
    return when (page) {
        ANIME_SOURCES_PAGE, ANIME_EXTENSIONS_PAGE, ANIME_MIGRATION_PAGE -> BrowseSearchTarget.ANIME
        MANGA_SOURCES_PAGE, MANGA_EXTENSIONS_PAGE, MANGA_MIGRATION_PAGE -> BrowseSearchTarget.MANGA
        else -> BrowseSearchTarget.UNKNOWN
    }
}

internal fun updateBrowseSearchTarget(
    lastKnownSearchTarget: BrowseSearchTarget,
    page: Int,
): BrowseSearchTarget {
    return when (val pageTarget = browseSearchTargetForPage(page)) {
        BrowseSearchTarget.UNKNOWN -> lastKnownSearchTarget
        else -> pageTarget
    }
}

internal fun resolveBrowseReselectTarget(lastKnownSearchTarget: BrowseSearchTarget): BrowseSearchTarget {
    return when (lastKnownSearchTarget) {
        BrowseSearchTarget.UNKNOWN -> BrowseSearchTarget.ANIME
        else -> lastKnownSearchTarget
    }
}
