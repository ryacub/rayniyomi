package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.create.BackupOptions
import eu.kanade.tachiyomi.data.backup.create.creators.MangaBackupCreator
import eu.kanade.tachiyomi.data.backup.models.BackupChapter
import eu.kanade.tachiyomi.data.backup.models.BackupManga
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.entries.manga.MangaRepositoryImpl
import tachiyomi.data.items.chapter.ChapterRepositoryImpl
import tachiyomi.domain.entries.manga.interactor.GetMangaByUrlAndSourceId
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter

@OptIn(ExperimentalSerializationApi::class)
class MangaMemoBackupTest {

    private val empty = JsonObject(emptyMap())
    private val mangaMemo = JsonObject(mapOf("slug" to JsonPrimitive("series-1a2b")))
    private val chapterMemo = JsonObject(mapOf("id" to JsonPrimitive(42)))

    private fun restorer(db: InMemoryMangaDb) = MangaRestorer(
        handler = db.handler,
        getCategories = mockk(relaxed = true),
        getMangaByUrlAndSourceId = GetMangaByUrlAndSourceId(MangaRepositoryImpl(db.handler)),
        getChaptersByMangaId = GetChaptersByMangaId(ChapterRepositoryImpl(db.handler)),
        updateManga = mockk(relaxed = true),
        getTracks = mockk(relaxed = true),
        insertTrack = mockk(relaxed = true),
        fetchInterval = mockk(relaxed = true),
    )

    private fun creator(db: InMemoryMangaDb) = MangaBackupCreator(
        handler = db.handler,
        getCategories = mockk(relaxed = true),
        getHistory = mockk(relaxed = true),
    )

    private suspend fun seed(db: InMemoryMangaDb): Manga {
        val mangas = MangaRepositoryImpl(db.handler)
        val id = mangas.insertManga(
            Manga.create().copy(source = 1, url = "/series/1", title = "Series", favorite = true, memo = mangaMemo),
        )!!
        ChapterRepositoryImpl(db.handler).addAllChapters(
            listOf(Chapter.create().copy(mangaId = id, url = "/ch/1", name = "1", memo = chapterMemo)),
        )
        return mangas.getMangaById(id)
    }

    private suspend fun storedMemos(db: InMemoryMangaDb): Pair<JsonObject, JsonObject> {
        val manga = MangaRepositoryImpl(db.handler).getMangaByUrlAndSourceId("/series/1", 1)!!
        val chapter = ChapterRepositoryImpl(db.handler).getChapterByMangaId(manga.id).single()
        return manga.memo to chapter.memo
    }

    @Test
    fun `memo survives a backup and restore round trip`() = runTest {
        val bytes = InMemoryMangaDb().use { source ->
            val backup = creator(source)(listOf(seed(source)), BackupOptions()).single()
            ProtoBuf.encodeToByteArray(BackupManga.serializer(), backup)
        }

        InMemoryMangaDb().use { target ->
            restorer(target).restore(ProtoBuf.decodeFromByteArray(BackupManga.serializer(), bytes), emptyList())

            assertEquals(mangaMemo to chapterMemo, storedMemos(target))
        }
    }

    @Test
    fun `a backup from before the memo fields restores with an empty memo`() = runTest {
        val backup = ProtoBuf.decodeFromByteArray(BackupManga.serializer(), legacyBackupBytes())

        InMemoryMangaDb().use { db ->
            restorer(db).restore(backup, emptyList())

            assertEquals(empty to empty, storedMemos(db))
        }
    }

    @Test
    fun `restoring over existing entries keeps their memo`() = runTest {
        val backup = ProtoBuf.decodeFromByteArray(BackupManga.serializer(), legacyBackupBytes(version = 99))

        InMemoryMangaDb().use { db ->
            seed(db)
            restorer(db).restore(backup, emptyList())

            assertEquals(mangaMemo to chapterMemo, storedMemos(db))
        }
    }

    @Test
    fun `a build without the memo fields reads a new backup`() {
        val backup = BackupManga(
            source = 1,
            url = "/series/1",
            title = "Series",
            chapters = listOf(BackupChapter(url = "/ch/1", name = "1")),
        )
        val bytes = ProtoBuf.encodeToByteArray(BackupManga.serializer(), backup)

        val legacy = ProtoBuf.decodeFromByteArray(LegacyBackupManga.serializer(), bytes)

        assertEquals("/ch/1", legacy.chapters.single().url)
    }

    private fun legacyBackupBytes(version: Long = 0): ByteArray = ProtoBuf.encodeToByteArray(
        LegacyBackupManga.serializer(),
        LegacyBackupManga(
            source = 1,
            url = "/series/1",
            title = "Series",
            chapters = listOf(LegacyBackupChapter(url = "/ch/1", name = "1", read = true)),
            version = version,
        ),
    )
}

@Serializable
private class LegacyBackupManga(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String = "",
    @ProtoNumber(16) val chapters: List<LegacyBackupChapter> = emptyList(),
    @ProtoNumber(109) val version: Long = 0,
)

@Serializable
private class LegacyBackupChapter(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(4) val read: Boolean = false,
)
