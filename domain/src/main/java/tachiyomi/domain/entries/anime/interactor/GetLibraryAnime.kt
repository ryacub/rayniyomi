package tachiyomi.domain.entries.anime.interactor

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.retry
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.library.anime.LibraryAnime
import tachiyomi.domain.library.model.LibraryFilter
import kotlin.time.Duration.Companion.seconds

class GetLibraryAnime(
    private val animeRepository: AnimeRepository,
) {

    suspend fun await(): List<LibraryAnime> {
        return animeRepository.getLibraryAnime()
    }

    suspend fun await(filter: LibraryFilter): List<LibraryAnime> {
        return animeRepository.getLibraryAnimeFiltered(filter)
    }

    fun subscribe(): Flow<List<LibraryAnime>> {
        return animeRepository.getLibraryAnimeAsFlow()
            .retry {
                if (it is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetLibraryAnime.logcat(LogPriority.ERROR, it)
            }
    }

    fun subscribe(filter: LibraryFilter): Flow<List<LibraryAnime>> {
        return animeRepository.getLibraryAnimeFilteredAsFlow(filter)
            .retry {
                if (it is NullPointerException) {
                    delay(0.5.seconds)
                    true
                } else {
                    false
                }
            }.catch {
                this@GetLibraryAnime.logcat(LogPriority.ERROR, it)
            }
    }
}
