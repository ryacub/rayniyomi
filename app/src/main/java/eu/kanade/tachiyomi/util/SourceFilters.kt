package eu.kanade.tachiyomi.util

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.model.FilterList
import kotlinx.coroutines.CancellationException
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

/**
 * Returns the source filters, or null when the extension cannot produce them.
 *
 * Extensions run their own code here across the child-first class loader boundary, so a defective
 * one raises [LinkageError] rather than a plain exception: shared library classes such as
 * `kotlinx.coroutines.BuildersKt` resolve from the app APK, and the extension's own copy can
 * disagree about their members.
 */
fun CatalogueSource.getFilterListOrNull(): FilterList? = runSourceFilterCatching({ name }, ::getFilterList)

fun AnimeCatalogueSource.getFilterListOrNull(): AnimeFilterList? = runSourceFilterCatching({ name }, ::getFilterList)

private inline fun <T> Any.runSourceFilterCatching(sourceName: () -> String, filters: () -> T): T? {
    return try {
        filters()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        // The name comes from the extension too, so a defective one can fail this call as well.
        val name = runCatching(sourceName).getOrDefault("unknown")
        logcat(LogPriority.ERROR, error) { "Extension filter error: $name" }
        null
    }
}
