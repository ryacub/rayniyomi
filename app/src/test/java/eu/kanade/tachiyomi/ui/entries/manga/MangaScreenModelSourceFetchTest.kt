package eu.kanade.tachiyomi.ui.entries.manga

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.lifecycle.viewModelScope
import eu.kanade.domain.base.BasePreferences
import eu.kanade.domain.source.manga.interactor.UpdateMangaFromRemote
import eu.kanade.domain.source.manga.model.RemoteMangaUpdate
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.entries.manga.components.ChapterDownloadAction
import eu.kanade.tachiyomi.data.backup.restore.restorers.InMemoryMangaDb
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mihon.domain.items.chapter.interactor.FilterChaptersForDownload
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.data.entries.manga.MangaRepositoryImpl
import tachiyomi.data.entries.manga.MangaSourceLinkRepositoryImpl
import tachiyomi.data.items.chapter.ChapterRepositoryImpl
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.model.LinkMangaSourcesResult
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaSourceLink
import tachiyomi.domain.entries.manga.repository.MangaSourceLinkRepository
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.service.MangaSourceManager
import tachiyomi.domain.track.manga.interactor.GetMangaTracks

@OptIn(ExperimentalCoroutinesApi::class)
class MangaScreenModelSourceFetchTest {

    private val vt = VirtualTime()
    private val updateMangaFromRemote = mockk<UpdateMangaFromRemote>()
    private val sourcePreferences = SourcePreferences(
        InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference("hidden_catalogues", null, emptySet<String>()),
                InMemoryPreferenceStore.InMemoryPreference("source_languages", null, setOf("en")),
            ),
        ),
    )
    private val filterChaptersForDownload = mockk<FilterChaptersForDownload> {
        coEvery { await(any(), any()) } answers { secondArg() }
    }
    private val downloadManager = mockk<MangaDownloadManager>(relaxed = true) {
        every { queueState } returns MutableStateFlow(emptyList())
        every { statusFlow() } returns emptyFlow()
        every { progressFlow() } returns emptyFlow()
    }
    private val entryRefresher by lazy {
        MangaEntryRefresher(
            updateMangaFromRemote = updateMangaFromRemote,
            filterChaptersForDownload = filterChaptersForDownload,
            downloadManager = downloadManager,
            dispatcher = vt.io,
        )
    }

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
        every { testAppGraph.mangaSourceManager } returns
            mockk<MangaSourceManager>(relaxed = true) { every { sources } returns flowOf(emptyList()) }
        every { testAppGraph.basePreferences } returns BasePreferences(mockk(relaxed = true), InMemoryPreferenceStore())
        every { testAppGraph.sourcePreferences } returns sourcePreferences
        coEvery { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) } answers {
            Result.success(RemoteMangaUpdate(manga = secondArg(), newChapters = emptyList()))
        }
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    @Test
    fun `a persisted source pair presents chapters from both originals as one numbered list`() = runTest(vt.scheduler) {
        InMemoryMangaDb().use { db ->
            db.insertManga(id = 1, url = "first", title = "First", source = 1)
            db.insertManga(id = 2, url = "second", title = "Second", source = 2)
            val mangas = MangaRepositoryImpl(db.handler)
            val first = mangas.getMangaById(1)
            val chapters = ChapterRepositoryImpl(db.handler)
            chapters.addAllChapters(
                (1..30).map { number ->
                    Chapter.create().copy(
                        id = number.toLong(),
                        mangaId = first.id,
                        url = "first-$number",
                        name = "Chapter $number",
                        chapterNumber = number.toDouble(),
                    )
                } + (25..40).map { number ->
                    Chapter.create().copy(
                        id = (100 + number).toLong(),
                        mangaId = 2,
                        url = "second-$number",
                        name = "Chapter $number",
                        chapterNumber = number.toDouble(),
                    )
                },
            )
            val sourceLinks = MangaSourceLinkRepositoryImpl(db.handler)
            sourceLinks.link(firstMangaId = 1, secondMangaId = 2) shouldBe LinkMangaSourcesResult.Linked
            val second = mangas.getMangaById(2)

            val model = createModel(
                manga = first,
                chapters = chapters.getChapterByMangaId(first.id),
                mangaSourceLinkRepository = sourceLinks,
                linkedManga = second,
                linkedChapters = chapters.getChapterByMangaId(second.id),
            )
            val firstState = model.state.first {
                it is MangaScreenModel.State.Success
            } as MangaScreenModel.State.Success
            val firstRows = firstState.chapterListItems.filterIsInstance<ChapterList.Item>()
            firstRows.size shouldBe 40
            firstRows.map { it.chapter.chapterNumber }.toSet() shouldBe (1..40).map(Int::toDouble).toSet()
            firstRows.single { it.chapter.chapterNumber == 25.0 }.versions.map { it.manga.id } shouldBe listOf(1L, 2L)

            val reversedModel = createModel(
                manga = second,
                chapters = chapters.getChapterByMangaId(second.id),
                mangaSourceLinkRepository = sourceLinks,
                linkedManga = first,
                linkedChapters = chapters.getChapterByMangaId(first.id),
            )
            val reversedState = reversedModel.state.first {
                it is MangaScreenModel.State.Success
            } as MangaScreenModel.State.Success
            val reversedRows = reversedState.chapterListItems.filterIsInstance<ChapterList.Item>()
            reversedRows.map { it.chapter.chapterNumber } shouldBe firstRows.map { it.chapter.chapterNumber }
            reversedRows.single { it.chapter.chapterNumber == 25.0 }.versions.map { it.manga.id } shouldBe
                listOf(1L, 2L)
        }
    }

    @Test
    fun `adding an empty linked edition fetches its details and chapters`() = runTest(vt.scheduler) {
        val primary = manga(initialized = true).copy(favorite = true)
        val partner = manga(id = 2L, source = 2L, initialized = true).copy(title = "Partner")
        val linkFlow = MutableStateFlow<MangaSourceLink?>(null)
        val linkRepository = mockk<MangaSourceLinkRepository>(relaxed = true) {
            coEvery { getForManga(primary.id) } returns null
            every { getForMangaAsFlow(primary.id) } returns linkFlow
        }
        val primarySource = mockk<MangaSource>(relaxed = true) {
            every { id } returns primary.source
            every { lang } returns "en"
            every { name } returns "Primary"
        }
        val partnerSource = mockk<MangaSource>(relaxed = true) {
            every { id } returns partner.source
            every { lang } returns "en"
            every { name } returns "Partner"
        }
        every { testAppGraph.mangaSourceManager } returns mockk<MangaSourceManager>(relaxed = true) {
            every { sources } returns flowOf(listOf(primarySource, partnerSource))
            every { getAll() } returns listOf(primarySource, partnerSource)
            every { get(primary.source) } returns primarySource
            every { get(partner.source) } returns partnerSource
            every { getOrStub(primary.source) } returns primarySource
            every { getOrStub(partner.source) } returns partnerSource
        }
        val model = createModel(
            manga = primary,
            chapters = listOf(chapter()),
            mangaSourceLinkRepository = linkRepository,
            linkedManga = partner,
        )
        advanceUntilIdle()

        linkFlow.value = MangaSourceLink(primary.id, partner.id)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            updateMangaFromRemote(
                source = partnerSource,
                manga = partner,
                fetchDetails = true,
                fetchChapters = true,
                manualFetch = true,
            )
        }
    }

    @Test
    fun `a disabled linked source stays unavailable when its chapter flow emits`() = runTest(vt.scheduler) {
        val primary = manga(initialized = true).copy(favorite = true)
        val partner = manga(id = 2L, source = 2L, initialized = true).copy(title = "Partner")
        val link = MangaSourceLink(primary.id, partner.id)
        val linkedChapters = MutableStateFlow(partner to emptyList<Chapter>())
        val linkRepository = mockk<MangaSourceLinkRepository>(relaxed = true) {
            coEvery { getForManga(primary.id) } returns link
            every { getForMangaAsFlow(primary.id) } returns flowOf(link)
        }
        val primarySource = mockk<MangaSource>(relaxed = true) {
            every { id } returns primary.source
            every { lang } returns "en"
            every { name } returns "Primary"
        }
        val partnerSource = mockk<MangaSource>(relaxed = true) {
            every { id } returns partner.source
            every { lang } returns "en"
            every { name } returns "Partner"
        }
        every { testAppGraph.mangaSourceManager } returns mockk<MangaSourceManager>(relaxed = true) {
            every { sources } returns flowOf(listOf(primarySource, partnerSource))
            every { getAll() } returns listOf(primarySource, partnerSource)
            every { get(primary.source) } returns primarySource
            every { get(partner.source) } returns partnerSource
            every { getOrStub(primary.source) } returns primarySource
            every { getOrStub(partner.source) } returns partnerSource
        }
        val disabledSourcePreferences = SourcePreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference(
                        "hidden_catalogues",
                        setOf(partner.source.toString()),
                        emptySet<String>(),
                    ),
                    InMemoryPreferenceStore.InMemoryPreference("source_languages", setOf("en"), emptySet<String>()),
                ),
            ),
        )
        val model = createModel(
            manga = primary,
            chapters = listOf(chapter()),
            mangaSourceLinkRepository = linkRepository,
            linkedManga = partner,
            linkedChaptersFlow = linkedChapters,
            sourcePreferences = disabledSourcePreferences,
        )
        advanceUntilIdle()

        val initialState = model.state.value as MangaScreenModel.State.Success
        initialState.linkedManga?.id shouldBe partner.id
        initialState.linkedSourceUnavailable shouldBe true

        linkedChapters.value = partner to listOf(chapter(id = 21L, mangaId = partner.id))
        advanceUntilIdle()

        model.successState().linkedChapters.map { it.chapter.id } shouldBe listOf(21L)
        model.successState().linkedSourceUnavailable shouldBe true
    }

    @Test
    fun `a repeated source pair keeps the linked snapshot and refresh failure`() = runTest(vt.scheduler) {
        val primary = manga(initialized = true).copy(favorite = true)
        val partner = manga(id = 2L, source = 2L, initialized = false).copy(title = "Partner")
        val link = MangaSourceLink(primary.id, partner.id)
        val linkEvents = MutableSharedFlow<MangaSourceLink?>(replay = 1)
        linkEvents.tryEmit(link)
        var linkedSubscriptions = 0
        val linkedSnapshot: Flow<Pair<Manga, List<Chapter>>> = flow {
            linkedSubscriptions++
            if (linkedSubscriptions == 1) {
                emit(partner to emptyList())
            } else {
                CompletableDeferred<Unit>().await()
            }
        }
        val linkRepository = mockk<MangaSourceLinkRepository>(relaxed = true) {
            coEvery { getForManga(primary.id) } returns link
            every { getForMangaAsFlow(primary.id) } returns linkEvents
        }
        val primarySource = mockk<MangaSource>(relaxed = true) {
            every { id } returns primary.source
            every { lang } returns "en"
            every { name } returns "Primary"
        }
        val partnerSource = mockk<MangaSource>(relaxed = true) {
            every { id } returns partner.source
            every { lang } returns "en"
            every { name } returns "Partner"
        }
        every { testAppGraph.mangaSourceManager } returns mockk<MangaSourceManager>(relaxed = true) {
            every { sources } returns flowOf(listOf(primarySource, partnerSource))
            every { getAll() } returns listOf(primarySource, partnerSource)
            every { get(primary.source) } returns primarySource
            every { get(partner.source) } returns partnerSource
            every { getOrStub(primary.source) } returns primarySource
            every { getOrStub(partner.source) } returns partnerSource
        }
        coEvery { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("partner refresh failed"))
        val model = createModel(
            manga = primary,
            chapters = listOf(chapter()),
            mangaSourceLinkRepository = linkRepository,
            linkedManga = partner,
            linkedChaptersFlow = linkedSnapshot,
        )
        advanceUntilIdle()

        model.successState().linkedSourceRefreshFailed shouldBe true
        model.successState().linkedManga?.id shouldBe partner.id
        linkedSubscriptions shouldBe 1

        linkEvents.emit(link)
        runCurrent()

        model.successState().linkedSourceRefreshFailed shouldBe true
        model.successState().linkedManga?.id shouldBe partner.id
        linkedSubscriptions shouldBe 1
    }

    @Test
    fun `a linked chapter download updates the linked row`() = runTest(vt.scheduler) {
        val primary = manga(initialized = true).copy(favorite = true)
        val partner = manga(id = 2L, source = 2L, initialized = true).copy(title = "Partner")
        val partnerChapter = chapter(id = 21L, mangaId = partner.id)
        val link = MangaSourceLink(primary.id, partner.id)
        val linkRepository = mockk<MangaSourceLinkRepository>(relaxed = true) {
            coEvery { getForManga(primary.id) } returns link
            every { getForMangaAsFlow(primary.id) } returns flowOf(link)
        }
        val primarySource = mockk<MangaSource>(relaxed = true) {
            every { id } returns primary.source
            every { lang } returns "en"
            every { name } returns "Primary"
        }
        val partnerSource = mockk<MangaSource>(relaxed = true) {
            every { id } returns partner.source
            every { lang } returns "en"
            every { name } returns "Partner"
        }
        every { testAppGraph.mangaSourceManager } returns mockk<MangaSourceManager>(relaxed = true) {
            every { sources } returns flowOf(listOf(primarySource, partnerSource))
            every { getAll() } returns listOf(primarySource, partnerSource)
            every { get(primary.source) } returns primarySource
            every { get(partner.source) } returns partnerSource
            every { getOrStub(primary.source) } returns primarySource
            every { getOrStub(partner.source) } returns partnerSource
        }
        val downloadStatuses = MutableStateFlow<MangaDownload?>(null)
        every { downloadManager.statusFlow() } returns downloadStatuses.filterNotNull()
        val lifecycleOwner = TestLifecycleOwner(Lifecycle.State.RESUMED, vt.main)
        val model = createModel(
            manga = primary,
            chapters = listOf(chapter()),
            mangaSourceLinkRepository = linkRepository,
            linkedManga = partner,
            linkedChapters = listOf(partnerChapter),
            lifecycle = lifecycleOwner.lifecycle,
            fetchInterval = MangaFetchInterval(mockk()),
        )
        advanceUntilIdle()
        model.state.filterIsInstance<MangaScreenModel.State.Success>()
            .first { state -> state.linkedChapters.any { it.id == partnerChapter.id } }
        downloadStatuses.subscriptionCount.first { it > 0 }

        val download = MangaDownload(mockk<HttpSource>(), partner, partnerChapter).apply {
            status = MangaDownload.State.DOWNLOADED
        }
        downloadStatuses.value = download
        advanceUntilIdle()

        model.successState().linkedChapters.single().downloadState shouldBe MangaDownload.State.DOWNLOADED
        model.successState().chapters.single().downloadState shouldBe MangaDownload.State.NOT_DOWNLOADED
    }

    @Test
    fun `linked chapter download deletion and translation use the original owner`() = runTest(vt.scheduler) {
        val primary = manga(initialized = true).copy(favorite = true)
        val partner = manga(id = 2L, source = 2L, initialized = true).copy(title = "Partner")
        val partnerChapter = chapter(id = 21L, mangaId = partner.id)
        val link = MangaSourceLink(primary.id, partner.id)
        val linkRepository = mockk<MangaSourceLinkRepository>(relaxed = true) {
            coEvery { getForManga(primary.id) } returns link
            every { getForMangaAsFlow(primary.id) } returns flowOf(link)
        }
        val primarySource = mockk<MangaSource>(relaxed = true) {
            every { id } returns primary.source
            every { lang } returns "en"
            every { name } returns "Primary"
        }
        val partnerSource = mockk<MangaSource>(relaxed = true) {
            every { id } returns partner.source
            every { lang } returns "en"
            every { name } returns "Partner"
        }
        every { testAppGraph.mangaSourceManager } returns mockk<MangaSourceManager>(relaxed = true) {
            every { sources } returns flowOf(listOf(primarySource, partnerSource))
            every { getAll() } returns listOf(primarySource, partnerSource)
            every { get(primary.source) } returns primarySource
            every { get(partner.source) } returns partnerSource
            every { getOrStub(primary.source) } returns primarySource
            every { getOrStub(partner.source) } returns partnerSource
        }
        val translationManager = mockk<TranslationManager>(relaxed = true) {
            every { translationStates } returns MutableStateFlow(emptyMap())
        }
        val model = createModel(
            manga = primary,
            chapters = listOf(chapter()),
            mangaSourceLinkRepository = linkRepository,
            linkedManga = partner,
            linkedChapters = listOf(partnerChapter),
            translationManager = translationManager,
        )
        advanceUntilIdle()
        val item = ChapterList.Item(
            chapter = partnerChapter,
            downloadState = MangaDownload.State.NOT_DOWNLOADED,
            downloadProgress = 0,
        )

        model.runChapterDownloadActions(listOf(item), ChapterDownloadAction.START)
        advanceUntilIdle()
        model.deleteChapters(listOf(partnerChapter))
        advanceUntilIdle()
        model.translateChapter(partnerChapter)
        model.deleteTranslation(partnerChapter)

        verify { downloadManager.downloadChapters(partner, listOf(partnerChapter)) }
        verify { downloadManager.deleteChapters(listOf(partnerChapter), partner, partnerSource) }
        verify { translationManager.translateChapter(partner, partnerChapter, partnerSource) }
        verify { translationManager.deleteTranslation(partnerChapter, partner.title, partnerSource) }
    }

    @Test
    fun `opening an initialized manga with chapters does not call the source`() = runTest(vt.scheduler) {
        createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()

        coVerify(exactly = 0) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `opening a manga that is not initialized fetches details only`() = runTest(vt.scheduler) {
        createModel(manga = manga(initialized = false), chapters = listOf(chapter()))
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
        coVerify { updateMangaFromRemote(any(), any(), fetchDetails = true, fetchChapters = false, any(), any()) }
    }

    @Test
    fun `opening a manga with no chapters fetches chapters only`() = runTest(vt.scheduler) {
        createModel(manga = manga(initialized = true), chapters = emptyList())
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
        coVerify { updateMangaFromRemote(any(), any(), fetchDetails = false, fetchChapters = true, any(), any()) }
    }

    @Test
    fun `a manual refresh fetches details and chapters`() = runTest(vt.scheduler) {
        val model = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()

        model.fetchAllFromSource()
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
        coVerify { updateMangaFromRemote(any(), any(), fetchDetails = true, fetchChapters = true, any(), any()) }
    }

    @Test
    fun `a manual refresh finishes and queues new chapters after the screen is closed`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        sourceReturnsAfter(gate, newChapters = listOf(chapter(id = 11L)))
        val model = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()

        model.fetchAllFromSource()
        advanceUntilIdle()
        model.viewModelScope.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        verify { downloadManager.downloadChapters(any(), listOf(chapter(id = 11L))) }
    }

    @Test
    fun `the refresh indicator shows while a manual refresh runs`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        sourceReturnsAfter(gate)
        val model = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()

        model.fetchAllFromSource()
        advanceUntilIdle()
        val whileRunning = model.successState().isRefreshingData
        gate.complete(Unit)
        advanceUntilIdle()

        whileRunning shouldBe true
        model.successState().isRefreshingData shouldBe false
    }

    @Test
    fun `a failed manual refresh shows the error in a snackbar`() = runTest(vt.scheduler) {
        val model = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()
        coEvery { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("source down"))

        model.fetchAllFromSource()
        advanceUntilIdle()

        model.snackbarHostState.currentSnackbarData?.visuals?.message shouldBe "IllegalStateException: source down"
        model.successState().isRefreshingData shouldBe false
    }

    @Test
    fun `a failed refresh of a library title offers recovery for its source`() = runTest(vt.scheduler) {
        val model =
            createModel(
                manga = manga(initialized = true).copy(favorite = true, source = 7L),
                chapters = listOf(chapter()),
            )
        advanceUntilIdle()
        coEvery { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("source down"))

        model.fetchAllFromSource()
        advanceUntilIdle()
        val snackbar = checkNotNull(model.snackbarHostState.currentSnackbarData)
        val request = async { model.recoveryRequests.first() }
        snackbar.performAction()
        advanceUntilIdle()

        (snackbar.visuals.actionLabel != null) shouldBe true
        request.await() shouldBe 7L
    }

    @Test
    fun `a failed refresh of a title outside the library offers no recovery`() = runTest(vt.scheduler) {
        val model = createModel(manga = manga(initialized = true).copy(favorite = false), chapters = listOf(chapter()))
        advanceUntilIdle()
        coEvery { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) } returns
            Result.failure(IllegalStateException("source down"))

        model.fetchAllFromSource()
        advanceUntilIdle()

        model.snackbarHostState.currentSnackbarData?.visuals?.actionLabel shouldBe null
    }

    @Test
    fun `a second manual refresh while one runs does not fetch again`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        sourceReturnsAfter(gate)
        val model = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()

        model.fetchAllFromSource()
        model.fetchAllFromSource()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `reopening the screen while a refresh runs shows the indicator until it ends`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        sourceReturnsAfter(gate)
        val closed = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()
        closed.fetchAllFromSource()
        advanceUntilIdle()
        closed.viewModelScope.cancel()

        val reopened = createModel(manga = manga(initialized = true), chapters = listOf(chapter()))
        advanceUntilIdle()
        val whileRunning = reopened.successState().isRefreshingData
        gate.complete(Unit)
        advanceUntilIdle()

        whileRunning shouldBe true
        reopened.successState().isRefreshingData shouldBe false
        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a manual refresh during the initial load does not fetch again`() = runTest(vt.scheduler) {
        val gate = CompletableDeferred<Unit>()
        sourceReturnsAfter(gate)
        val model = createModel(manga = manga(initialized = false), chapters = listOf(chapter()))
        advanceUntilIdle()

        model.fetchAllFromSource()
        gate.complete(Unit)
        advanceUntilIdle()

        coVerify(exactly = 1) { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) }
    }

    private fun sourceReturnsAfter(gate: CompletableDeferred<Unit>, newChapters: List<Chapter> = emptyList()) {
        coEvery { updateMangaFromRemote(any(), any(), any(), any(), any(), any()) } coAnswers {
            gate.await()
            Result.success(RemoteMangaUpdate(manga = secondArg(), newChapters = newChapters))
        }
    }

    private fun MangaScreenModel.successState() = state.value as MangaScreenModel.State.Success

    private fun createModel(
        manga: Manga,
        chapters: List<Chapter>,
        mangaSourceLinkRepository: MangaSourceLinkRepository = mockk(relaxed = true) {
            coEvery { getForManga(any()) } returns null
            every { getForMangaAsFlow(any()) } returns flowOf(null)
        },
        linkedManga: Manga? = null,
        linkedChapters: List<Chapter> = emptyList(),
        linkedChaptersFlow: Flow<Pair<Manga, List<Chapter>>>? = null,
        sourcePreferences: SourcePreferences = this.sourcePreferences,
        lifecycle: Lifecycle = mockk(relaxed = true),
        fetchInterval: MangaFetchInterval = testAppGraph.mangaFetchInterval,
        translationManager: TranslationManager = mockk {
            every { translationStates } returns MutableStateFlow(emptyMap())
        },
    ): MangaScreenModel {
        val getMangaAndChapters = mockk<GetMangaWithChapters> {
            coEvery { subscribe(any(), any()) } answers {
                val id = firstArg<Long>()
                if (linkedManga?.id == id) linkedChaptersFlow ?: flowOf(linkedManga to linkedChapters) else emptyFlow()
            }
            coEvery { awaitManga(manga.id) } returns manga
            coEvery { awaitChapters(manga.id, any()) } returns chapters
            if (linkedManga != null) {
                coEvery { awaitManga(linkedManga.id) } returns linkedManga
                coEvery { awaitChapters(linkedManga.id, any()) } returns linkedChapters
            }
        }
        return MangaScreenModel(
            context = mockk(relaxed = true),
            lifecycle = lifecycle,
            mangaId = manga.id,
            isFromSource = false,
            trackChapter = testAppGraph.trackChapter,
            downloadCache = testAppGraph.mangaDownloadCache,
            getDuplicateLibraryManga = mockk(relaxed = true),
            getAvailableScanlators = mockk(relaxed = true),
            getExcludedScanlators = mockk(relaxed = true),
            setExcludedScanlators = mockk(relaxed = true),
            setMangaChapterFlags = mockk(relaxed = true),
            setMangaDefaultChapterFlags = mockk(relaxed = true),
            setReadStatus = mockk(relaxed = true),
            updateChapter = testAppGraph.updateChapter,
            updateManga = testAppGraph.updateManga,
            getCategories = testAppGraph.getMangaCategories,
            addTracks = testAppGraph.addMangaTracks,
            setMangaCategories = mockk(relaxed = true),
            mangaRepository = testAppGraph.mangaRepository,
            fetchInterval = fetchInterval,
            refreshTracksInteractor = mockk(relaxed = true),
            sourceManager = testAppGraph.mangaSourceManager,
            mangaSourceLinkRepository = mangaSourceLinkRepository,
            libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()),
            trackPreferences = TrackPreferences(InMemoryPreferenceStore()),
            readerPreferences = ReaderPreferences(InMemoryPreferenceStore()),
            trackerManager = mockk<TrackerManager> { every { loggedInTrackersFlow() } returns emptyFlow() },
            downloadManager = downloadManager,
            getMangaAndChapters = getMangaAndChapters,
            getTracks = mockk<GetMangaTracks> { every { subscribe(any<Long>()) } returns emptyFlow() },
            updateMangaFromRemote = updateMangaFromRemote,
            entryRefresher = entryRefresher,
            translationManager = translationManager,
            ioDispatcher = vt.io,
            sourcePreferences = sourcePreferences,
            getMangaHistory = testAppGraph.getMangaHistory,
        )
    }

    private fun manga(
        initialized: Boolean,
        id: Long = 1L,
        source: Long = 1L,
    ) = Manga.create().copy(id = id, source = source, initialized = initialized)

    private fun chapter(id: Long = 10L, mangaId: Long = 1L) = Chapter.create().copy(id = id, mangaId = mangaId)
}
