package eu.kanade.tachiyomi.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.manga.interactor.GetMangaIncognitoState
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryMangaDb
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.data.translation.TranslationPreferences
import eu.kanade.tachiyomi.data.translation.TranslationStorageManager
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.test.VirtualTime
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.data.entries.manga.MangaSourceLinkRepositoryImpl
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.history.manga.interactor.GetNextChapters
import tachiyomi.domain.history.manga.interactor.UpsertMangaHistory
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.interactor.UpdateChapter
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.service.MangaSourceManager
import java.io.IOException

class ReaderViewModelLinkedNavigationTest {

    private val vt = VirtualTime()
    private val previousGraph = AppGraphHolder.graphOrNull

    @BeforeEach
    fun setUp() = vt.setUpMain()

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
        AppGraphHolder.graphOrNull = previousGraph
    }

    @Test
    fun `next from the initiating source latest chapter advances to the linked source`() = runTest(vt.scheduler) {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "a", title = "A", source = 11)
            db.insertManga(id = 2, url = "b", title = "B", source = 12)
            db.insertChapter(id = 30, mangaId = 1, url = "a-30")
            db.insertChapter(id = 130, mangaId = 2, url = "b-30")
            db.insertChapter(id = 31, mangaId = 2, url = "b-31")
            db.insertChapter(id = 33, mangaId = 1, url = "a-33")
            val linkRepository = MangaSourceLinkRepositoryImpl(db.handler)
            assertEquals(
                tachiyomi.domain.entries.manga.model.LinkMangaSourcesResult.Linked,
                linkRepository.link(1, 2),
            )

            val manga = Manga.create().copy(id = 1L, source = 11L, title = "A")
            val partner = Manga.create().copy(id = 2L, source = 12L, title = "B")
            val chapters = (1L..33L).filterNot { it == 31L || it == 32L }.map { id ->
                Chapter.create().copy(
                    id = id,
                    mangaId = manga.id,
                    url = "a-$id",
                    name = "Chapter $id",
                    chapterNumber = id.toDouble(),
                )
            }
            val partnerChapters = (25L..40L).map { id ->
                Chapter.create().copy(
                    id = if (id == 31L) 31L else id + 100,
                    mangaId = partner.id,
                    url = "b-$id",
                    name = "Chapter $id",
                    chapterNumber = id.toDouble(),
                    lastPageRead = if (id == 31L) 4L else 0L,
                )
            }
            val getManga = mockk<GetManga> {
                coEvery { await(1L) } returns manga
                coEvery { await(2L) } returns partner
            }
            val getChapters = mockk<GetChaptersByMangaId> {
                coEvery { await(1L, applyScanlatorFilter = true) } returns chapters
                coEvery { await(2L, applyScanlatorFilter = true) } returns partnerChapters
            }
            val source = mockk<HttpSource>(relaxed = true) {
                every { id } returns 11L
                every { lang } returns "en"
                coEvery { getPageList(any()) } returns listOf(Page(index = 0, url = "page", imageUrl = "image"))
            }
            val partnerSource = mockk<HttpSource>(relaxed = true) {
                every { id } returns 12L
                every { lang } returns "en"
                coEvery { getPageList(any()) } returns listOf(Page(index = 0, url = "partner-page", imageUrl = "image"))
            }
            val sourceManager = mockk<MangaSourceManager>(relaxed = true) {
                every { isInitialized } returns MutableStateFlow(true)
                every { get(11L) } returns source
                every { get(12L) } returns partnerSource
                every { getOrStub(11L) } returns source
                every { getOrStub(12L) } returns partnerSource
            }
            val readerPreferences = mockk<eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences>(relaxed = true) {
                every { skipRead().get() } returns false
                every { skipFiltered().get() } returns false
                every { skipDupe().get() } returns false
                every { showTranslatedPages().get() } returns false
            }
            val translationPreferences = mockk<TranslationPreferences>(relaxed = true) {
                every { targetLanguage().get() } returns "en"
            }
            val disabledSources = InMemoryPreferenceStore.InMemoryPreference(
                "hidden_catalogues",
                null,
                emptySet<String>(),
            )
            val enabledLanguages = InMemoryPreferenceStore.InMemoryPreference("source_languages", null, setOf("en"))
            val sourcePrefs = SourcePreferences(InMemoryPreferenceStore(sequenceOf(disabledSources, enabledLanguages)))
            AppGraphHolder.graph = mockk<AppGraph>(relaxed = true) {
                every { chapterCache } returns mockk<ChapterCache> {
                    every { getPageListFromCache(any()) } throws IOException("not cached")
                }
                every { sourcePreferences } returns sourcePrefs
                every { this@mockk.readerPreferences } returns readerPreferences
                every { this@mockk.translationPreferences } returns translationPreferences
                every { translationStorageManager } returns mockk<TranslationStorageManager>(relaxed = true)
            }
            val downloadManager = mockk<MangaDownloadManager>(relaxed = true) {
                every { isChapterDownloaded(any(), any(), any(), any(), any()) } returns false
            }
            val translationManager = mockk<TranslationManager> {
                every { languageGeneration } returns MutableStateFlow(0)
                every { translationStates } returns MutableStateFlow(emptyMap())
            }
            val savedState = SavedStateHandle()
            val model = ReaderViewModel(
                savedState = savedState,
                sourceManager = sourceManager,
                downloadManager = downloadManager,
                downloadProvider = mockk(relaxed = true),
                imageSaver = mockk(relaxed = true),
                readerPreferences = readerPreferences,
                basePreferences = mockk<BasePreferences>(relaxed = true) {
                    every { downloadedOnly().get() } returns false
                },
                downloadPreferences = mockk<DownloadPreferences>(relaxed = true) {
                    every { autoDownloadWhileReading().get() } returns 0
                },
                trackPreferences = mockk<TrackPreferences>(relaxed = true),
                trackChapter = mockk(relaxed = true),
                getManga = getManga,
                mangaSourceLinkRepository = linkRepository,
                getChaptersByMangaId = getChapters,
                getNextChapters = mockk<GetNextChapters>(relaxed = true),
                upsertHistory = mockk<UpsertMangaHistory>(relaxed = true),
                updateChapter = mockk<UpdateChapter>(relaxed = true),
                setMangaViewerFlags = mockk(relaxed = true),
                getIncognitoState = mockk(relaxed = true),
                sourcePreferences = sourcePrefs,
                libraryPreferences = mockk<LibraryPreferences>(relaxed = true),
                translationStorageManager = mockk(relaxed = true),
                translationPreferences = translationPreferences,
                translationManager = translationManager,
                application = mockk(relaxed = true),
            )
            try {
                val initialized = model.init(mangaId = 1L, initialChapterId = 30L)
                assertEquals(true, initialized.getOrThrow())
                assertEquals(30L, model.state.value.currentChapter?.chapter?.id)

                model.loadNextChapter()

                assertEquals(31L, model.state.value.currentChapter?.chapter?.id)
                assertEquals(4, model.state.value.currentChapter?.requestedPage)
                assertEquals(listOf(31L, 4L), savedState.get<LongArray>("reader_checkpoint")?.toList())
                assertEquals(partnerSource, model.getSource())
                coVerify(timeout = 5_000) {
                    partnerSource.getPageList(match { it.url == "b-31" })
                }

                model.loadPreviousChapter()

                assertEquals(130L, model.state.value.currentChapter?.chapter?.id)

                model.loadNextChapter()

                assertEquals(31L, model.state.value.currentChapter?.chapter?.id)

                disabledSources.set(setOf("12"))
                assertEquals(setOf("12"), sourcePrefs.disabledMangaSources().get())
                model.loadNextChapter()

                assertEquals(33L, model.state.value.currentChapter?.chapter?.id)
            } finally {
                model.viewModelScope.cancel()
            }
        }
    }
}
