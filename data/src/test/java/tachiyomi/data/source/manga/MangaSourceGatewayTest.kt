package tachiyomi.data.source.manga

import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.source.online.ResolvableSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import tachiyomi.core.common.util.lang.SourceLinkageException
import tachiyomi.core.common.util.lang.SourceLinkageReporter

class MangaSourceGatewayTest {

    @AfterEach
    fun tearDown() {
        SourceLinkageReporter.onFailure = {}
    }

    @TestFactory
    fun `each entry point reports a LinkageError with the source name`(): List<DynamicTest> {
        val manga = SManga.create()
        val chapter = SChapter.create()
        val page = Page(0)

        return listOf(
            failingCall<CatalogueSource>("popular") {
                coEvery { getPopularManga(any()) } throws linkageError()
                MangaSourceGateway.popular(this, 1)
            },
            failingCall<CatalogueSource>("search") {
                coEvery { getSearchManga(any(), any(), any()) } throws linkageError()
                MangaSourceGateway.search(this, 1, "query", FilterList())
            },
            failingCall<CatalogueSource>("latest") {
                coEvery { getLatestUpdates(any()) } throws linkageError()
                MangaSourceGateway.latest(this, 1)
            },
            failingCall<MangaSource>("pages") {
                coEvery { getPageList(any()) } throws linkageError()
                MangaSourceGateway.pages(this, chapter)
            },
            failingCall<CatalogueSource>("filters") {
                every { getFilterList() } throws linkageError()
                MangaSourceGateway.filters(this)
            },
            failingCall<HttpSource>("image URL") {
                coEvery { getImageUrl(any()) } throws linkageError()
                MangaSourceGateway.imageUrl(this, page)
            },
            failingCall<HttpSource>("image") {
                coEvery { getImage(any()) } throws linkageError()
                MangaSourceGateway.image(this, page)
            },
            failingCall<HttpSource>("manga URL") {
                every { getMangaUrl(any()) } throws linkageError()
                MangaSourceGateway.mangaUrl(this, manga)
            },
            failingCall<HttpSource>("chapter URL") {
                every { getChapterUrl(any()) } throws linkageError()
                MangaSourceGateway.chapterUrl(this, chapter)
            },
            failingCall<HttpSource>("prepare chapter") {
                every { prepareNewChapter(any(), any()) } throws linkageError()
                MangaSourceGateway.prepareChapter(this, chapter, manga)
            },
            failingCall<ResolvableSource>("URI type") {
                every { getUriType(any()) } throws linkageError()
                MangaSourceGateway.uriType(this, "https://example.invalid")
            },
            failingCall<ResolvableSource>("manga from URI") {
                coEvery { getManga(any()) } throws linkageError()
                MangaSourceGateway.mangaFromUri(this, "https://example.invalid")
            },
            failingCall<ResolvableSource>("chapter from URI") {
                coEvery { getChapter(any()) } throws linkageError()
                MangaSourceGateway.chapterFromUri(this, "https://example.invalid")
            },
        ).map { (name, test) -> DynamicTest.dynamicTest(name) { runBlocking { test() } } }
    }

    @Test
    fun `preference screen reports a LinkageError and stays empty`() {
        val source = mockk<ConfigurableSource> {
            every { name } returns "Broken Manga Source"
            every { setupPreferenceScreen(any()) } throws linkageError()
        }
        val reported = mutableListOf<SourceLinkageException>()
        SourceLinkageReporter.onFailure = { reported += it }

        MangaSourceGateway.setupPreferences(source, mockk<PreferenceScreen>())

        reported.single().sourceName shouldBe "Broken Manga Source"
    }

    @Test
    fun `display name reports a LinkageError and returns a class fallback`() {
        val source = object : MangaSource {
            override val id = 1L
            override val name = "Broken Manga Source"
            override val supportsLatest = false

            override suspend fun getPopularManga(page: Int): MangasPage = throw UnsupportedOperationException()
            override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()
            override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage =
                throw UnsupportedOperationException()
            override suspend fun getMangaUpdate(
                manga: SManga,
                chapters: List<SChapter>,
                fetchDetails: Boolean,
                fetchChapters: Boolean,
            ): SMangaUpdate = throw UnsupportedOperationException()
            override suspend fun getPageList(chapter: SChapter): List<Page> = throw UnsupportedOperationException()

            override fun toString(): String = throw linkageError()
        }
        val reported = mutableListOf<SourceLinkageException>()
        SourceLinkageReporter.onFailure = { reported += it }

        MangaSourceGateway.displayName(source) shouldBe source.javaClass.name

        reported.single().sourceName shouldBe "Broken Manga Source"
    }

    @Test
    fun `cancellation leaves the gateway without a report`() {
        val source = mockk<MangaSource> {
            every { name } returns "Example"
            coEvery { getPageList(any()) } throws CancellationException("cancelled")
        }
        val reported = mutableListOf<SourceLinkageException>()
        SourceLinkageReporter.onFailure = { reported += it }

        shouldThrow<CancellationException> {
            runBlocking { MangaSourceGateway.pages(source, SChapter.create()) }
        }

        reported shouldBe emptyList()
    }

    @Test
    fun `image transforms the URL for the request and restores the page`() {
        val page = Page(0, imageUrl = "https://example.invalid/original.jpg")
        val response = mockk<Response>()
        var requestedUrl: String? = null
        val source = mockk<HttpSource> {
            every { name } returns "Example"
            coEvery { getImage(page) } answers {
                requestedUrl = page.imageUrl
                response
            }
        }

        val result = runBlocking {
            MangaSourceGateway.image(source, page) { "compressed:$it" }
        }

        result shouldBe response
        requestedUrl shouldBe "compressed:https://example.invalid/original.jpg"
        page.imageUrl shouldBe "https://example.invalid/original.jpg"
    }

    @Test
    fun `image restores the URL when the request fails`() {
        val page = Page(0, imageUrl = "https://example.invalid/original.jpg")
        val source = mockk<HttpSource> {
            every { name } returns "Broken Manga Source"
            coEvery { getImage(page) } throws linkageError()
        }

        shouldThrow<SourceLinkageException> {
            runBlocking {
                MangaSourceGateway.image(source, page) { "compressed:$it" }
            }
        }

        page.imageUrl shouldBe "https://example.invalid/original.jpg"
    }

    private inline fun <reified T : MangaSource> failingCall(
        name: String,
        crossinline call: suspend T.() -> Unit,
    ): Pair<String, suspend () -> Unit> {
        return name to {
            val source = mockk<T> {
                every { this@mockk.name } returns "Broken Manga Source"
            }
            val reported = mutableListOf<SourceLinkageException>()
            SourceLinkageReporter.onFailure = { reported += it }

            val failure = shouldThrow<SourceLinkageException> { source.call() }

            failure.sourceName shouldBe "Broken Manga Source"
            failure.cause.shouldBeInstanceOf<NoSuchMethodError>()
            reported shouldBe listOf(failure)
        }
    }

    private fun linkageError() = NoSuchMethodError("runBlockingK\$default")
}
