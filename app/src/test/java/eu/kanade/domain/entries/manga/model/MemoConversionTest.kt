package eu.kanade.domain.entries.manga.model

import eu.kanade.domain.items.chapter.model.copyFromSChapter
import eu.kanade.domain.items.chapter.model.toSChapter
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.items.chapter.model.Chapter

class MemoConversionTest {

    private val memo = JsonObject(mapOf("slug" to JsonPrimitive("series-1a2b")))

    @Test
    fun `manga conversions carry the memo both ways`() {
        assertEquals(memo, Manga.create().copy(memo = memo).toSManga().memo)

        val remote = SManga.create().apply {
            url = "/series/1"
            title = "Series"
            memo = this@MemoConversionTest.memo
        }
        assertEquals(memo, remote.toDomainManga(sourceId = 1).memo)
        assertEquals(memo, Manga.create().copyFrom(remote).memo)
    }

    @Test
    fun `chapter conversions carry the memo both ways`() {
        assertEquals(memo, Chapter.create().copy(memo = memo).toSChapter().memo)

        val remote = SChapter.create().apply {
            url = "/ch/1"
            name = "1"
            memo = this@MemoConversionTest.memo
        }
        assertEquals(memo, Chapter.create().copyFromSChapter(remote).memo)
    }
}
