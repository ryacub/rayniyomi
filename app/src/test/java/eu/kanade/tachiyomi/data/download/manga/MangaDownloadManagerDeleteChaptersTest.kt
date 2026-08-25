package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.MangaSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

@OptIn(ExperimentalCoroutinesApi::class)
class MangaDownloadManagerDeleteChaptersTest {

    private val context = mockk<Context>(relaxed = true)
    private val source = mockk<MangaSource>()
    private val manga = Manga.create().copy(id = 1L, title = "Test Manga")
    private val chapter = Chapter.create().copy(id = 11L, name = "Chapter 1", scanlator = null)

    private lateinit var provider: MangaDownloadProvider
    private lateinit var mangaDir: FakeNode

    /** Minimal directory node whose delete removes it from its parent's children map. */
    private inner class FakeNode(val nodeName: String, val parent: FakeNode? = null) {
        val mock: UniFile = mockk("node:$nodeName")
        val children = LinkedHashMap<String, FakeNode>()

        init {
            every { mock.name } returns nodeName
            every { mock.isDirectory } returns true
            every { mock.isFile } returns false
            every { mock.listFiles() } answers { children.values.map { it.mock }.toTypedArray() }
            every { mock.delete() } answers {
                parent?.children?.remove(nodeName)
                true
            }
        }

        fun addChild(name: String): FakeNode = children.getOrPut(name) { FakeNode(name, this) }
    }

    @BeforeEach
    fun setUp() {
        provider = mockk()
        mangaDir = FakeNode("Test Manga")
    }

    /** A scope shaped like [MangaDownloadManager.scope], driven by the runTest scheduler. */
    private fun buildManager(scope: CoroutineScope): MangaDownloadManager {
        val excludePref = mockk<Preference<Set<String>>> {
            every { get() } returns emptySet()
        }
        val bookmarkPref = mockk<Preference<Boolean>> {
            every { get() } returns false
        }
        val downloadPreferences = mockk<DownloadPreferences> {
            every { removeExcludeCategories() } returns excludePref
            every { removeBookmarkedChapters() } returns bookmarkPref
        }

        val downloader = mockk<MangaDownloader>(relaxed = true) {
            every { queueState } returns MutableStateFlow(emptyList())
        }

        return MangaDownloadManager(
            context = context,
            storageManager = mockk(relaxed = true),
            provider = provider,
            cache = mockk(relaxed = true),
            getCategories = mockk {
                coEvery { await(any()) } returns emptyList()
            },
            sourceManager = mockk(relaxed = true),
            downloadPreferences = downloadPreferences,
            downloaderForTesting = downloader,
            scopeForTesting = scope,
        )
    }

    private fun stubSingleChapterDeletion() {
        val cbzNode = mangaDir.addChild("Chapter 1.cbz")
        val sidecarNode = mangaDir.addChild("Chapter 1.cbz_translated")

        every { provider.findChapterDirs(listOf(chapter), manga, source) } returns
            Pair<UniFile?, List<UniFile>>(mangaDir.mock, listOf(cbzNode.mock))
        every { provider.findTranslationSidecarDirs(listOf(chapter), manga, source) } returns listOf(sidecarNode.mock)
        every { provider.findMangaDir(manga.title, source) } returns mangaDir.mock
        every { provider.findSourceDir(source) } returns null
    }


    @Test
    fun `deleting a translated chapter removes its sidecar before the empty-folder check`() = runTest {
        stubSingleChapterDeletion()
        val manager = buildManager(
            CoroutineScope(
                SupervisorJob() +
                    kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            ),
        )

        manager.deleteChapters(listOf(chapter), manga, source)
        advanceUntilIdle()

        // The manga folder emptied because the sidecar went first.
        verify(exactly = 1) { mangaDir.mock.delete() }
    }

    @Test
    fun `sidecars of remaining chapters keep the manga folder`() = runTest {
        stubSingleChapterDeletion()
        // A second downloaded chapter keeps the folder non-empty.
        mangaDir.addChild("Chapter 2.cbz")
        val manager = buildManager(
            CoroutineScope(
                SupervisorJob() +
                    kotlinx.coroutines.test.StandardTestDispatcher(testScheduler),
            ),
        )

        manager.deleteChapters(listOf(chapter), manga, source)
        advanceUntilIdle()

        assertFalse(mangaDir.children.containsKey("Chapter 1.cbz"))
        assertFalse(mangaDir.children.containsKey("Chapter 1.cbz_translated"))
        assertTrue(mangaDir.children.containsKey("Chapter 2.cbz"))
        verify(exactly = 0) { mangaDir.mock.delete() }
    }
}
