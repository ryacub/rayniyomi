package eu.kanade.tachiyomi.data.download.core

import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadStore
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadStore
import eu.kanade.tachiyomi.source.online.HttpSource
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.entries.anime.interactor.GetAnime
import tachiyomi.domain.entries.manga.interactor.GetManga
import tachiyomi.domain.items.chapter.interactor.GetChapter
import tachiyomi.domain.items.episode.interactor.GetEpisode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager

class DownloadStoreRestoreTest {
    private val context = mockk<Context>()
    private val ids = listOf(12L, 11L)

    @Test
    fun `anime restore preserves pending items before the caller republishes them`() = runTest {
        seed("anime", "animeId", "episodeId")
        val sources = mockk<AnimeSourceManager>()
        val getAnime = mockk<GetAnime>()
        val getEpisode = mockk<GetEpisode>()
        every { sources.get(5L) } returns mockk<AnimeHttpSource>()
        coEvery { getAnime.await(7L) } returns mockk {
            every { id } returns 7L
            every { source } returns 5L
        }
        ids.forEach { itemId ->
            coEvery { getEpisode.await(itemId) } returns mockk { every { id } returns itemId }
        }

        assertEquals(ids, AnimeDownloadStore(context, sources, Json, getAnime, getEpisode).restore().map { it.episode.id })
        assertEquals(ids, AnimeDownloadStore(context, sources, Json, getAnime, getEpisode).restore().map { it.episode.id })
    }

    @Test
    fun `manga restore preserves pending items before the caller republishes them`() = runTest {
        seed("manga", "mangaId", "chapterId")
        val sources = mockk<MangaSourceManager>()
        val getManga = mockk<GetManga>()
        val getChapter = mockk<GetChapter>()
        every { sources.get(5L) } returns mockk<HttpSource>()
        coEvery { getManga.await(7L) } returns mockk {
            every { id } returns 7L
            every { source } returns 5L
        }
        ids.forEach { itemId ->
            coEvery { getChapter.await(itemId) } returns mockk { every { id } returns itemId }
        }

        assertEquals(ids, MangaDownloadStore(context, sources, Json, getManga, getChapter).restore().map { it.chapter.id })
        assertEquals(ids, MangaDownloadStore(context, sources, Json, getManga, getChapter).restore().map { it.chapter.id })
    }

    private fun seed(media: String, parentField: String, itemField: String) {
        val entries = mutableMapOf<String, Any>()
        ids.forEachIndexed { order, id ->
            entries[id.toString()] = """{"$parentField":7,"$itemField":$id,"order":$order}"""
        }
        preferences("active_downloads_$media", entries)
        preferences("active_downloads")
        preferences("active_downloads_migration", mutableMapOf("${media}_active_downloads_migrated" to true))
    }

    private fun preferences(name: String, entries: MutableMap<String, Any> = mutableMapOf()) {
        val preferences = mockk<SharedPreferences>()
        every { context.getSharedPreferences(name, Context.MODE_PRIVATE) } returns preferences
        every { preferences.all } answers { entries.toMap() }
        every { preferences.getBoolean(any(), any()) } answers { entries[firstArg()] as? Boolean ?: secondArg() }
        every { preferences.edit() } answers {
            val editor = mockk<SharedPreferences.Editor>()
            val writes = mutableMapOf<String, Any>()
            var clear = false
            val apply = {
                if (clear) entries.clear()
                entries.putAll(writes)
            }
            every { editor.clear() } answers {
                clear = true
                editor
            }
            every { editor.putString(any(), any()) } answers {
                writes[firstArg()] = secondArg<String>()
                editor
            }
            every { editor.putBoolean(any(), any()) } answers {
                writes[firstArg()] = secondArg<Boolean>()
                editor
            }
            every { editor.apply() } answers { apply() }
            every { editor.commit() } answers {
                apply()
                true
            }
            editor
        }
    }
}
