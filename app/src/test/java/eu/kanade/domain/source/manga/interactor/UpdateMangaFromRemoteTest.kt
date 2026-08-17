package eu.kanade.domain.source.manga.interactor

import eu.kanade.domain.items.chapter.interactor.SyncChaptersWithSource
import eu.kanade.tachiyomi.data.cache.MangaCoverCache
import eu.kanade.tachiyomi.source.MangaSource
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.util.lang.SourceLinkageException
import tachiyomi.core.common.util.lang.SourceLinkageReporter
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.repository.ChapterRepository

class UpdateMangaFromRemoteTest {

    @AfterEach
    fun tearDown() {
        SourceLinkageReporter.onFailure = {}
    }

    @Test
    fun `a LinkageError from getMangaUpdate is contained and reported, not escaped`() {
        runBlocking {
            val source = mockk<MangaSource> {
                every { name } returns "Broken Manga Source"
                coEvery { getMangaUpdate(any(), any(), any(), any()) } throws NoSuchMethodError("runBlockingK\$default")
            }
            val chapterRepository = mockk<ChapterRepository> {
                coEvery { getChapterByMangaId(any()) } returns emptyList()
            }
            val reported = mutableListOf<SourceLinkageException>()
            SourceLinkageReporter.onFailure = { reported += it }

            val interactor = UpdateMangaFromRemote(
                sourceManager = mockk(relaxed = true),
                chapterRepository = chapterRepository,
                mangaRepository = mockk<MangaRepository>(relaxed = true),
                syncChaptersWithSource = mockk<SyncChaptersWithSource>(relaxed = true),
                coverCache = mockk<MangaCoverCache>(relaxed = true),
            )

            val result = interactor(source = source, manga = mockk<Manga>(relaxed = true))

            val failure = result.exceptionOrNull()
            failure.shouldBeInstanceOf<SourceLinkageException>()
            failure.cause.shouldBeInstanceOf<NoSuchMethodError>()
            reported.single().sourceName shouldBe "Broken Manga Source"
        }
    }
}
