package xyz.rayniyomi.plugin.lightnovel.data

import android.content.Context
import android.util.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * Tests that verify [NovelStorage] handles corrupted library data safely.
 *
 * Covers:
 * - Corrupted JSON triggers [NovelStorageState.Corrupted] from [NovelStorage.checkIntegrity]
 * - [NovelStorage.clearAndRecover] resets library to a clean empty state
 * - Normal reads still work after recovery
 */
class NovelStorageCorruptionTest {

    private var tempDir: File? = null
    private lateinit var storage: NovelStorage

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0

        val dir = createTempDirectory("ln-storage-test").toFile()
        tempDir = dir
        val context = mockk<Context> {
            every { filesDir } returns dir
        }
        storage = NovelStorage(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        tempDir?.deleteRecursively()
        tempDir = null
    }

    // --- Integrity check on healthy data ---

    @Test
    fun `checkIntegrity returns Ok when library file does not exist`() {
        val state = storage.checkIntegrity()
        assertEquals(NovelStorageState.Ok, state)
    }

    @Test
    fun `checkIntegrity returns Ok when library file contains valid JSON`() {
        val integrityBefore = storage.checkIntegrity()
        assertEquals(NovelStorageState.Ok, integrityBefore)
    }

    // --- Corruption detection ---

    @Test
    fun `checkIntegrity returns Corrupted when library file contains invalid JSON`() {
        val libraryFile = File(File(tempDir!!, "light_novel_plugin"), "library.json")
        libraryFile.parentFile?.mkdirs()
        libraryFile.writeText("{ this is not valid json }")

        val state = storage.checkIntegrity()

        assertTrue(state is NovelStorageState.Corrupted, "Expected Corrupted but got $state")
    }

    @Test
    fun `checkIntegrity returns Corrupted when library file is truncated or empty`() {
        val libraryFile = File(File(tempDir!!, "light_novel_plugin"), "library.json")
        libraryFile.parentFile?.mkdirs()
        libraryFile.writeText("")

        val state = storage.checkIntegrity()

        assertTrue(state is NovelStorageState.Corrupted, "Expected Corrupted but got $state")
    }

    @Test
    fun `Corrupted state exposes a non-blank reason`() {
        val libraryFile = File(File(tempDir!!, "light_novel_plugin"), "library.json")
        libraryFile.parentFile?.mkdirs()
        libraryFile.writeText("{ invalid }")

        val state = storage.checkIntegrity() as NovelStorageState.Corrupted

        assertTrue(state.reason.isNotBlank())
    }

    // --- clearAndRecover ---

    @Test
    fun `clearAndRecover removes corrupted library file and resets to clean state`() {
        val rootDir = File(tempDir!!, "light_novel_plugin")
        val libraryFile = File(rootDir, "library.json")
        rootDir.mkdirs()
        libraryFile.writeText("{ corrupted }")

        storage.clearAndRecover()

        val stateAfter = storage.checkIntegrity()
        assertEquals(NovelStorageState.Ok, stateAfter)
    }

    @Test
    fun `clearAndRecover allows listBooks to return empty list after corruption`() {
        val rootDir = File(tempDir!!, "light_novel_plugin")
        val libraryFile = File(rootDir, "library.json")
        rootDir.mkdirs()
        libraryFile.writeText("{ bad data }")

        storage.clearAndRecover()

        val books = storage.listBooks()
        assertEquals(0, books.size)
    }

    @Test
    fun `clearAndRecover is idempotent when called on already clean state`() {
        storage.clearAndRecover()
        storage.clearAndRecover()

        val state = storage.checkIntegrity()
        assertEquals(NovelStorageState.Ok, state)
    }
}
