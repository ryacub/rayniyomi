package eu.kanade.tachiyomi.ui.entries.manga

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.test.VirtualTime
import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.entries.manga.interactor.GetMangaWithChapters
import tachiyomi.domain.entries.manga.interactor.MangaFetchInterval
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.manga.service.MangaSourceManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class MangaScreenModelOutdatedListTest {

    private val vt = VirtualTime()
    private val fetchInterval = MangaFetchInterval(mockk())
    private val clock = MutableClock(Instant.parse("2026-03-10T12:00:00Z"))
    private val owner = TestLifecycleOwner(Lifecycle.State.RESUMED, vt.main)

    @BeforeEach
    fun setUp() {
        vt.setUpMain()
        every { testAppGraph.mangaSourceManager } returns mockk<MangaSourceManager>(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        vt.tearDownMain()
    }

    @Test
    fun `a caught-up library manga past its fetch window shows the outdated hint`() = runTest(vt.scheduler) {
        val model = createModel(staleManga())
        advanceUntilIdle()

        model.successState().isListOutdated shouldBe true
    }

    @Test
    fun `the hint hides while a refresh runs`() = runTest(vt.scheduler) {
        val model = createModel(staleManga())
        advanceUntilIdle()

        model.successState().copy(isRefreshingData = true).isListOutdated shouldBe false
    }

    @Test
    fun `a completed manga shows no hint`() = runTest(vt.scheduler) {
        val model = createModel(staleManga().copy(status = SManga.COMPLETED.toLong()))
        advanceUntilIdle()

        model.successState().isListOutdated shouldBe false
    }

    @Test
    fun `a manga outside the library shows no hint`() = runTest(vt.scheduler) {
        val model = createModel(staleManga().copy(favorite = false))
        advanceUntilIdle()

        model.successState().isListOutdated shouldBe false
    }

    @Test
    fun `the fetch window advances when the screen resumes`() = runTest(vt.scheduler) {
        val (windowStart, _) = fetchInterval.getWindow(ZonedDateTime.now(clock))
        val model = createModel(staleManga().copy(nextUpdate = windowStart))
        advanceUntilIdle()
        model.successState().isListOutdated shouldBe false

        owner.currentState = Lifecycle.State.CREATED
        advanceUntilIdle()
        clock.advance(1, ChronoUnit.DAYS)
        owner.currentState = Lifecycle.State.RESUMED
        advanceUntilIdle()

        model.successState().isListOutdated shouldBe true
    }

    private class MutableClock(private var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now

        fun advance(amount: Long, unit: ChronoUnit) {
            now = now.plus(amount, unit)
        }
    }

    private fun MangaScreenModel.successState() = state.value as MangaScreenModel.State.Success

    private fun staleManga(): Manga {
        val (windowStart, _) = fetchInterval.getWindow(ZonedDateTime.now(clock))
        return Manga.create().copy(
            id = 1L,
            source = 1L,
            favorite = true,
            initialized = true,
            nextUpdate = windowStart - 1,
        )
    }

    private fun createModel(manga: Manga): MangaScreenModel {
        val readChapter = Chapter.create().copy(id = 10L, mangaId = manga.id, read = true)
        val getMangaAndChapters = mockk<GetMangaWithChapters> {
            coEvery { subscribe(any(), any()) } returns emptyFlow()
            coEvery { awaitManga(manga.id) } returns manga
            coEvery { awaitChapters(manga.id, any()) } returns listOf(readChapter)
        }
        return MangaScreenModel(
            context = mockk(relaxed = true),
            lifecycle = owner.lifecycle,
            mangaId = manga.id,
            isFromSource = false,
            libraryPreferences = LibraryPreferences(InMemoryPreferenceStore()),
            trackPreferences = TrackPreferences(InMemoryPreferenceStore()),
            readerPreferences = ReaderPreferences(InMemoryPreferenceStore()),
            trackerManager = mockk<TrackerManager> { every { loggedInTrackersFlow() } returns emptyFlow() },
            downloadManager = mockk<MangaDownloadManager>(relaxed = true) {
                every { queueState } returns MutableStateFlow(emptyList())
                every { statusFlow() } returns emptyFlow()
                every { progressFlow() } returns emptyFlow()
            },
            getMangaAndChapters = getMangaAndChapters,
            getCategories = mockk<GetMangaCategories> { every { subscribe(manga.id) } returns flowOf(emptyList()) },
            getTracks = mockk { every { subscribe(any<Long>()) } returns emptyFlow() },
            translationManager = mockk<TranslationManager> {
                every { translationStates } returns MutableStateFlow(emptyMap())
            },
            fetchInterval = fetchInterval,
            clock = clock,
            ioDispatcher = vt.io,
        )
    }
}
