package xyz.rayniyomi.plugin.lightnovel.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NovelDataMigrationTest {

    // --- Schema version embedding ---

    @Test
    fun `NovelLibraryEnvelope defaults to LATEST_SCHEMA_VERSION`() {
        val envelope = NovelLibraryEnvelope(library = NovelLibrary())
        assertEquals(NovelSchemaMigrations.LATEST_SCHEMA_VERSION, envelope.schemaVersion)
    }

    // --- Migration v1 → v2 ---

    @Test
    fun `migration v1 to v2 sets readingTimeMinutes to zero when absent`() {
        val v1Book = NovelBook(
            id = "abc",
            title = "Test Book",
            epubFileName = "abc.epub",
            lastReadChapter = 3,
            lastReadOffset = 100,
            updatedAt = 1_000_000L,
        )
        val v1Library = NovelLibrary(books = listOf(v1Book))
        val v1Envelope = NovelLibraryEnvelope(schemaVersion = 1, library = v1Library)

        val v2Envelope = NovelSchemaMigrations.migrateToLatest(v1Envelope)

        assertEquals(2, v2Envelope.schemaVersion)
        assertEquals(1, v2Envelope.library.books.size)
        val migratedBook = v2Envelope.library.books.first()
        assertEquals("abc", migratedBook.id)
        assertEquals(3, migratedBook.lastReadChapter)
        assertEquals(0L, migratedBook.readingTimeMinutes)
    }

    @Test
    fun `migration v1 to v2 preserves all existing book fields`() {
        val v1Book = NovelBook(
            id = "id1",
            title = "My Novel",
            epubFileName = "id1.epub",
            lastReadChapter = 7,
            lastReadOffset = 250,
            updatedAt = 9_000_000L,
        )
        val v1Library = NovelLibrary(books = listOf(v1Book))
        val v1Envelope = NovelLibraryEnvelope(schemaVersion = 1, library = v1Library)

        val v2Envelope = NovelSchemaMigrations.migrateToLatest(v1Envelope)

        val book = v2Envelope.library.books.first()
        assertEquals("id1", book.id)
        assertEquals("My Novel", book.title)
        assertEquals("id1.epub", book.epubFileName)
        assertEquals(7, book.lastReadChapter)
        assertEquals(250, book.lastReadOffset)
        assertEquals(9_000_000L, book.updatedAt)
    }

    @Test
    fun `migration v1 to v2 handles empty library`() {
        val v1Envelope = NovelLibraryEnvelope(schemaVersion = 1, library = NovelLibrary())

        val v2Envelope = NovelSchemaMigrations.migrateToLatest(v1Envelope)

        assertEquals(2, v2Envelope.schemaVersion)
        assertEquals(0, v2Envelope.library.books.size)
    }

    @Test
    fun `migration v1 to v2 migrates multiple books`() {
        val books = listOf(
            NovelBook(id = "a", title = "A", epubFileName = "a.epub", updatedAt = 1L),
            NovelBook(id = "b", title = "B", epubFileName = "b.epub", updatedAt = 2L),
        )
        val v1Envelope = NovelLibraryEnvelope(schemaVersion = 1, library = NovelLibrary(books = books))

        val v2Envelope = NovelSchemaMigrations.migrateToLatest(v1Envelope)

        assertEquals(2, v2Envelope.library.books.size)
        v2Envelope.library.books.forEach { book ->
            assertEquals(0L, book.readingTimeMinutes)
        }
    }

    @Test
    fun `already at latest schema version returns same version unchanged`() {
        val latestVersion = NovelSchemaMigrations.LATEST_SCHEMA_VERSION
        val book = NovelBook(
            id = "z",
            title = "Z",
            epubFileName = "z.epub",
            readingTimeMinutes = 42L,
            updatedAt = 5L,
        )
        val envelope = NovelLibraryEnvelope(
            schemaVersion = latestVersion,
            library = NovelLibrary(books = listOf(book)),
        )

        val result = NovelSchemaMigrations.migrateToLatest(envelope)

        assertEquals(latestVersion, result.schemaVersion)
        assertEquals(42L, result.library.books.first().readingTimeMinutes)
    }

    @Test
    fun `LATEST_SCHEMA_VERSION is at least 2`() {
        assert(NovelSchemaMigrations.LATEST_SCHEMA_VERSION >= 2)
    }

    // --- Null safety on optional fields ---

    @Test
    fun `NovelBook readingTimeMinutes defaults to zero`() {
        val book = NovelBook(
            id = "x",
            title = "X",
            epubFileName = "x.epub",
            updatedAt = 1L,
        )
        assertEquals(0L, book.readingTimeMinutes)
    }
}
