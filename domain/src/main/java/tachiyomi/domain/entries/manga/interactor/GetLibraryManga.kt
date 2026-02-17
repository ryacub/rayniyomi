package tachiyomi.domain.entries.manga.interactor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.library.manga.LibraryManga
import tachiyomi.domain.library.model.LibraryFilter
import kotlin.time.Duration.Companion.seconds

class GetLibraryManga(
    private val mangaRepository: MangaRepository,
) {

    suspend fun await(): List<LibraryManga> {
        return mangaRepository.getLibraryManga()
    }

    suspend fun await(filter: LibraryFilter): List<LibraryManga> {
        return mangaRepository.getLibraryMangaFiltered(filter)
    }

    fun subscribe(): Flow<List<LibraryManga>> {
        return mangaRepository.getLibraryMangaAsFlow()
            .retry {
                if (it is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetLibraryManga.logcat(LogPriority.ERROR, it)
            }
    }

    fun subscribe(filter: LibraryFilter): Flow<List<LibraryManga>> {
        return mangaRepository.getLibraryMangaFilteredAsFlow(filter)
            .retry {
                if (it is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetLibraryManga.logcat(LogPriority.ERROR, it)
            }
    }
}
