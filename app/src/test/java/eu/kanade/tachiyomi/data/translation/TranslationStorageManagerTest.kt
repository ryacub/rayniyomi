package eu.kanade.tachiyomi.data.translation

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.source.MangaSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

class TranslationStorageManagerTest {

    private val provider = mockk<MangaDownloadProvider>()
    private val source = mockk<MangaSource>()

    private val manager = TranslationStorageManager(provider)

    // Fixture roots, rebuilt per test.
    private lateinit var mangaDir: FakeNode
    private lateinit var chapterNode: FakeNode

    private val chapterName = "Chapter 1"
    private val mangaTitle = "Test Manga"

    /**
     * A minimal in-memory UniFile tree node. Directories record children by name.
     * [asArchive] flips the node into a plain file that rejects children, which is
     * the real behaviour that made writes no-ops for CBZ chapters.
     */
    private inner class FakeNode(val nodeName: String) {
        val mock: UniFile = mockk("node:$nodeName")
        val children = LinkedHashMap<String, FakeNode>()

        /** Set when the node is attached to a parent, so delete removes it from the tree. */
        var parent: FakeNode? = null

        init {
            every { mock.name } returns nodeName
            every { mock.isDirectory } returns true
            every { mock.isFile } returns false
            every { mock.findFile(any<String>()) } answers { children[firstArg()]?.mock }
            every { mock.createDirectory(any<String>()) } answers {
                attach(children.getOrPut(firstArg()) { FakeNode(firstArg()) }).mock
            }
            every { mock.createFile(any<String>()) } answers {
                attach(children.getOrPut(firstArg()) { newFakeFile(firstArg()) }).mock
            }
            every { mock.listFiles() } answers { children.values.map { it.mock }.toTypedArray() }
            every { mock.delete() } answers {
                parent?.children?.remove(nodeName)
                true
            }
        }

        private fun attach(child: FakeNode): FakeNode {
            child.parent = this
            return child
        }

        fun asArchive(): FakeNode {
            every { mock.isFile } returns true
            every { mock.isDirectory } returns false
            every { mock.createDirectory(any<String>()) } returns null
            return this
        }
    }

    /** A leaf that accepts output streams; written bytes are discarded. */
    private fun newFakeFile(name: String): FakeNode {
        val node = FakeNode(name)
        every { node.mock.isFile } returns true
        every { node.mock.isDirectory } returns false
        every { node.mock.openOutputStream() } answers { ByteArrayOutputStream() }
        return node
    }

    @BeforeEach
    fun setUp() {
        mangaDir = FakeNode(mangaTitle)
        every { provider.findMangaDir(mangaTitle, source) } returns mangaDir.mock
    }

    /** Loose fixture: the chapter is a folder of images. */
    private fun looseChapter(): FakeNode {
        chapterNode = FakeNode(chapterName)
        every { provider.findChapterDir(chapterName, null, mangaTitle, source) } returns chapterNode.mock
        return chapterNode
    }

    /** CBZ fixture: the chapter resolves to an archive file next to which translations must go. */
    private fun cbzChapter(): FakeNode {
        chapterNode = FakeNode("$chapterName.cbz").asArchive()
        every { provider.findChapterDir(chapterName, null, mangaTitle, source) } returns chapterNode.mock
        return chapterNode
    }

    @Test
    fun `cbz chapter writes translated page into a sidecar folder next to the archive`() {
        cbzChapter()

        val result = manager.writeTranslatedPage(
            chapterName, null, mangaTitle, source, "en", "001.jpg",
            byteArrayOf(1, 2, 3),
        )

        assertEquals("001.jpg", result?.name)
        val langDir = mangaDir.children["Chapter 1.cbz_translated"]!!
            .children["en"]
        assertTrue(langDir != null && langDir.children.containsKey("001.jpg"))
    }

    @Test
    fun `cbz chapter writes metadata inside the language folder`() {
        cbzChapter()

        manager.writeMetadata(chapterName, null, mangaTitle, source, "en", "claude")

        val langDir = mangaDir.children["Chapter 1.cbz_translated"]!!.children["en"]
        assertTrue(langDir != null && langDir.children.containsKey(".translation_meta"))
    }

    @Test
    fun `cbz chapter reports translated after a page is written`() {
        cbzChapter()
        manager.writeTranslatedPage(
            chapterName, null, mangaTitle, source, "en", "001.jpg",
            byteArrayOf(1),
        )

        assertTrue(manager.isChapterTranslated(chapterName, null, mangaTitle, source, "en"))
    }

    @Test
    fun `cbz chapter resolves a stored translated page by index`() {
        cbzChapter()
        manager.writeTranslatedPage(
            chapterName, null, mangaTitle, source, "en", "001.jpg",
            byteArrayOf(1),
        )

        assertEquals("001.jpg", manager.getTranslatedPageFile(chapterName, null, mangaTitle, source, "en", 0)?.name)
        assertNull(manager.getTranslatedPageFile(chapterName, null, mangaTitle, source, "en", 1))
    }

    @Test
    fun `cbz chapter deletes one language and leaves other languages`() {
        cbzChapter()
        manager.writeTranslatedPage(chapterName, null, mangaTitle, source, "en", "001.jpg", byteArrayOf(1))
        manager.writeTranslatedPage(chapterName, null, mangaTitle, source, "fr", "001.jpg", byteArrayOf(1))

        assertTrue(manager.deleteTranslation(chapterName, null, mangaTitle, source, "en"))

        val sidecar = mangaDir.children["Chapter 1.cbz_translated"]!!
        assertFalse(sidecar.children.containsKey("en"))
        assertTrue(sidecar.children.containsKey("fr"))
    }

    @Test
    fun `cbz chapter deletes all languages by removing the sidecar folder`() {
        cbzChapter()
        manager.writeTranslatedPage(chapterName, null, mangaTitle, source, "en", "001.jpg", byteArrayOf(1))
        manager.writeTranslatedPage(chapterName, null, mangaTitle, source, "fr", "001.jpg", byteArrayOf(1))
        val sidecar = mangaDir.mock.findFile("Chapter 1.cbz_translated")!!

        assertTrue(manager.deleteAllTranslations(chapterName, null, mangaTitle, source))

        verify(exactly = 1) { sidecar.delete() }
    }

    @Test
    fun `loose chapter keeps the existing layout and never consults the manga dir`() {
        looseChapter()

        manager.writeTranslatedPage(chapterName, null, mangaTitle, source, "en", "001.jpg", byteArrayOf(1))
        manager.writeMetadata(chapterName, null, mangaTitle, source, "en", "claude")

        val langDir = chapterNode.children[TranslationStorageLayout.TRANSLATED_DIR]!!.children["en"]
        assertTrue(langDir != null && langDir.children.containsKey("001.jpg"))
        verify(exactly = 0) { provider.findMangaDir(any(), any()) }
    }

    @Test
    fun `missing chapter dir returns null and writes nothing`() {
        every { provider.findChapterDir(chapterName, null, mangaTitle, source) } returns null

        val result = manager.writeTranslatedPage(
            chapterName, null, mangaTitle, source, "en", "001.jpg",
            byteArrayOf(1),
        )

        assertNull(result)
        verify(exactly = 0) { provider.findMangaDir(any(), any()) }
    }
}
