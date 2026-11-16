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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.source.manga.service.MangaSourceManager

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
    private lateinit var model: LinkMangaSourceScreenModel

    @BeforeEach
    fun setUp() {
        time.setUpMain()
        val getManga = mockk<GetManga> {
            coEvery { await(original.id) } returns original
        }
        val sourceManager = mockk<MangaSourceManager> {
            every { getOnlineSources() } returns listOf(alternative)
            every { getOrStub(original.source) } returns mockk {
                every { name } returns "Source A"
            }
        }
        val preferences = SourcePreferences(InMemoryPreferenceStore(emptySequence()))
        preferences.enabledLanguages().set(setOf("en"))
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
        coEvery { alternative.getSearchManga(1, "Paper Moon", any()) } coAnswers {
            started.complete(Unit)
            finish.await()
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
            model.state.value.results shouldBe emptyList()
        } finally {
            finish.complete(MangasPage(emptyList(), false))
        }
    }
}
