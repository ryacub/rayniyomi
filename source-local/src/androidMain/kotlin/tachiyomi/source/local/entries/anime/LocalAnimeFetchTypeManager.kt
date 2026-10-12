package tachiyomi.source.local.entries.anime

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.FetchType
import tachiyomi.source.local.io.ArchiveAnime
import tachiyomi.source.local.io.anime.LocalAnimeSourceFileSystem

actual class LocalAnimeFetchTypeManager(
    private val context: Context,
    private val fileSystem: LocalAnimeSourceFileSystem,
) {
    actual fun find(animeUrl: String): FetchType = find(fileSystem.getFilesInAnimeDirectory(animeUrl))

    actual fun find(animeDirFiles: List<UniFile>): FetchType {
        return when {
            animeDirFiles.any { ArchiveAnime.isSupported(it) } -> FetchType.Episodes
            animeDirFiles.any { it.isDirectory } -> FetchType.Seasons
            else -> FetchType.Episodes
        }
    }
}
