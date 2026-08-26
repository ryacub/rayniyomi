package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.source.MangaSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

@OptIn(ExperimentalCoroutinesApi::class)
class MangaDownloadManagerRenameChapterTest {

    private val source = mockk<MangaSource>()
    private val manga = Manga.create().copy(id = 1L, title = "Test Manga")

    private lateinit var provider: MangaDownloadProvider
    private lateinit var mangaDir: FakeNode

    /** Directory or file node whose renameTo re-keys it in its parent's children map. */
    private inner class FakeNode(
        initialName: String,
        private val isFileNode: Boolean = false,
        private val parent: FakeNode? = null,
        private val renameSucceeds: Boolean = true,
    ) {
        var nodeName: String = initialName
            private set
        val mock: UniFile = mockk("node:$initialName")
        val children = LinkedHashMap<String, FakeNode>()

        init {
            every { mock.name } answers { nodeName }
            every { mock.isFile } returns isFileNode
            every { mock.isDirectory } returns !isFileNode
            every { mock.findFile(any()) } answers { children[firstArg<String>()]?.mock }
            every { mock.listFiles() } answers { children.values.map { it.mock }.toTypedArray() }
            every { mock.renameTo(any()) } answers {
                if (!renameSucceeds) {
                    false
                } else {
                    val target = firstArg<String>()
                    parent?.children?.remove(nodeName)
                    nodeName = target
                    parent?.children?.put(target, this@FakeNode)

                    true
                }
            }
        }

        fun addChild(
            name: String,
            isFileNode: Boolean = false,
            renameSucceeds: Boolean = true,
        ): FakeNode = FakeNode(name, isFileNode, this, renameSucceeds)
            .also { children[name] = it }
    }

    @BeforeEach
    fun setUp() {
        provider = mockk()
        mangaDir = FakeNode("Test Manga")
    }

    private fun buildManager(
        scope: CoroutineScope,
        cache: MangaDownloadCache = mockk(relaxed = true),
    ): MangaDownloadManager {
        val downloader = mockk<MangaDownloader>(relaxed = true) {
            every { queueState } returns MutableStateFlow(emptyList())
        }
        return MangaDownloadManager(
            context = mockk(relaxed = true),
            storageManager = mockk(relaxed = true),
            provider = provider,
            cache = cache,
            getCategories = mockk(relaxed = true),
            sourceManager = mockk(relaxed = true),
            downloadPreferences = mockk(relaxed = true),
            downloaderForTesting = downloader,
            scopeForTesting = scope,
        )
    }

    private fun stubArchiveProvider() {
        every { provider.getValidChapterDirNames("Chapter 1", null) } returns
            listOf("Chapter 1", "Chapter 1.cbz")
        every { provider.getMangaDir(manga.title, source) } returns mangaDir.mock
        every { provider.getChapterDirName("Chapter 2", null) } returns "Chapter 2"
    }

    @Test
    fun `renaming an archive chapter moves its translation sidecar`() = runTest {
        stubArchiveProvider()
        mangaDir.addChild("Chapter 1.cbz", isFileNode = true)
        mangaDir.addChild("Chapter 1.cbz_translated")
        val manager = buildManager(
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )

        manager.renameChapter(source, manga, chapterNamed("Chapter 1"), chapterNamed("Chapter 2"))

        assertTrue(mangaDir.children.containsKey("Chapter 2.cbz"))
        assertTrue(mangaDir.children.containsKey("Chapter 2.cbz_translated"))
        assertFalse(mangaDir.children.containsKey("Chapter 1.cbz"))
        assertFalse(mangaDir.children.containsKey("Chapter 1.cbz_translated"))
    }

    private fun chapterNamed(name: String): Chapter = Chapter.create().copy(id = 11L, name = name, scanlator = null)
}
