package eu.kanade.tachiyomi.ui.entries.manga

import androidx.lifecycle.viewModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.test.VirtualTime
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class LinkMangaSourceScreenModelTest {
    private val time = VirtualTime()
    private val original = Manga.create().copy(id = 1, source = 10, title = "Paper Moon")
    private val alternative = mockk<HttpSource> {
        every { id } returns 20
        every { name } returns "Source B"
        every { lang } returns "en"
        every { getFilterList() } returns mockk()
    }
    private val preferenceStore = SourcePreferences(InMemoryPreferenceStore(emptySequence()))
    private val disabled = preferenceStore.disabledMangaSources()
    private val languages = preferenceStore.enabledLanguages()
    private val preferences = mockk<SourcePreferences> {
        every { disabledMangaSources() } returns disabled
        every { enabledLanguages() } returns languages
    }
    private lateinit var model: LinkMangaSourceScreenModel

    @BeforeEach
    fun setUp() {
        time.setUpMain()
        val getManga = mockk<GetManga> {
            coEvery { await(original.id) } returns original
        }
        val sourceManager = mockk<MangaSourceManager> {
            every { sources } returns MutableStateFlow(listOf(alternative))
            every { getOnlineSources() } returns listOf(alternative)
            every { getOrStub(original.source) } returns mockk {
                every { name } returns "Source A"
            }
        }
        languages.set(setOf("en"))
        model = LinkMangaSourceScreenModel(original.id, getManga, sourceManager, preferences, mockk())
    }

    @AfterEach
    fun tearDown() {
        model.viewModelScope.cancel()
        time.tearDownMain()
    }

    @Test
    fun `picker opens with editable title and waits for an explicit search`() {
        model.state.value.query shouldBe original.title
        coVerify(exactly = 0) { alternative.getSearchManga(any(), any(), any()) }
        model.setQuery("An alias")
        model.state.value.query shouldBe "An alias"
    }

    @Test
    fun `editing the title invalidates a search that is still in flight`() = runTest(time.scheduler) {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<MangasPage>()
        val sourceReturned = CompletableDeferred<Unit>()
        coEvery { alternative.getSearchManga(1, "Paper Moon", any()) } coAnswers {
            started.complete(Unit)
            try {
                finish.await()
            } finally {
                sourceReturned.complete(Unit)
            }
        }

        try {
            model.selectSource(alternative)
            model.setQuery("Paper Moon")
            model.search()
            started.await()
            model.state.value.searching shouldBe true

            model.setQuery("An alias")
            model.state.value.query shouldBe "An alias"
            model.state.value.searching shouldBe false
            sourceReturned.await()
            model.state.value.results shouldBe emptyList()
        } finally {
            finish.complete(MangasPage(emptyList(), false))
        }
    }

    @Test
    fun `a source timeout ends the search and shows a failure`() = runTest(time.scheduler, timeout = 5.seconds) {
        coEvery { alternative.getSearchManga(1, "Paper Moon", any()) } coAnswers {
            withTimeout(50) { awaitCancellation() }
        }

        model.selectSource(alternative)
        model.search()
        val settled = model.state.first { !it.searching }
        settled.error shouldBe LinkMangaSourceError.SearchFailed
        settled.results shouldBe emptyList()
    }

    @Test
    fun `disabling the selected source clears the picker selection`() = runTest(time.scheduler) {
        model.selectSource(alternative)
        disabled.set(setOf(alternative.id.toString()))
        advanceUntilIdle()

        model.state.value.sources shouldBe emptyList()
        model.state.value.source shouldBe null
    }
}
