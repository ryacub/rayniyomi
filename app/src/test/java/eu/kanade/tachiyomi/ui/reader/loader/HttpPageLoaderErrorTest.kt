package eu.kanade.tachiyomi.ui.reader.loader

import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.sourceHelpUrl
import eu.kanade.tachiyomi.core.common.Constants
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.database.models.manga.ChapterImpl
import eu.kanade.tachiyomi.network.interceptor.CloudflareBypassException
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.reader.model.ReaderChapter
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import java.io.IOException

class HttpPageLoaderErrorTest {

    @Test
    fun `a failed page keeps its error so help can open the cloudflare section`() = runBlocking<Unit> {
        val source = mockk<HttpSource> {
            every { id } returns 1L
            every { name } returns "Blocked source"
            coEvery { getImageUrl(any()) } throws
                IOException("Failed to bypass Cloudflare", CloudflareBypassException())
        }
        val chapter = ReaderChapter(ChapterImpl(id = 2L).apply { url = "chapter-1" })
        val loader = HttpPageLoader(
            chapter = chapter,
            source = source,
            chapterCache = mockk<ChapterCache>(relaxed = true),
            sourcePreferences = SourcePreferences(InMemoryPreferenceStore()),
        )
        val page = ReaderPage(index = 0, url = "page-1").also { it.chapter = chapter }

        loader.retryPage(page)
        withTimeout(5_000) { page.statusFlow.first { it == Page.State.ERROR } }

        sourceHelpUrl(page.error) shouldBe Constants.URL_HELP_CLOUDFLARE
    }
}
