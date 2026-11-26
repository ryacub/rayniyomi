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
import io.mockk.verify
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
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
        val active = seed("anime", "animeId", "episodeId")
        val seeded = active.all
        val sourcesReady = MutableStateFlow(false)
        val createStore = animeStoreFactory(sourcesReady)
        val restoring = async(start = CoroutineStart.UNDISPATCHED) { createStore().restore() }

        assertFalse(restoring.isCompleted)
        assertEquals(seeded, active.all)
        sourcesReady.value = true
        assertEquals(ids, restoring.await().map { it.episode.id })
        assertEquals(ids, createStore().restore().map { it.episode.id })
    }

    @Test
    fun `failed rollback marker leaves restored queue ready and retries on reopen`() = runTest {
        val active = mutableMapOf<String, Any>()
        val legacy = entries("animeId", "episodeId")
        preferences("active_downloads_anime", active)
        val legacyPreferences = preferences("active_downloads", legacy)
        preferences("active_downloads_migration", commitSucceeds = { false })
        val createStore = animeStoreFactory()

        assertEquals(ids, createStore().restore().map { it.episode.id })
        assertEquals(ids.map(Long::toString).toSet(), active.keys.filter { it.toLongOrNull() != null }.toSet())

        val oldFlags = mutableMapOf<String, Any>()
        preferences("active_downloads_migration", oldFlags)

        assertEquals(ids, createStore().restore().map { it.episode.id })
        assertEquals(true, oldFlags["anime_active_downloads_migrated"])
        verify(exactly = 1) { legacyPreferences.all }
    }

    @Test
    fun `active write retries the staged entries before marking the old migration complete`() = runTest {
        val oldFlags = mutableMapOf<String, Any>()
        var writable = false
        preferences("active_downloads_anime", commitSucceeds = { writable })
        val legacy = entries("animeId", "episodeId")
        val legacyPreferences = preferences("active_downloads", legacy)
        preferences("active_downloads_migration", oldFlags)

        val restoring = async(start = CoroutineStart.UNDISPATCHED) { animeStoreFactory()().restore() }

        assertFalse(restoring.isCompleted)
        assertEquals(emptyMap<String, Any>(), oldFlags)
        legacy.clear()
        writable = true
        assertEquals(ids, restoring.await().map { it.episode.id })
        assertEquals(true, oldFlags["anime_active_downloads_migrated"])
        verify(exactly = 1) { legacyPreferences.all }
    }

    @Test
    fun `failed active reorder restores the old preferences after three attempts`() = runTest {
        var writable = true
        var attempts = 0
        val active = preferences("active_downloads_anime", entries("animeId", "episodeId")) {
            attempts++
            writable
        }
        preferences("active_downloads")
        preferences("active_downloads_migration", mutableMapOf("anime_active_downloads_migrated" to true))
        val store = animeStoreFactory()()
        val restored = store.restore()
        val previous = active.all
        attempts = 0
        writable = false

        val failure = runCatching { store.replaceAll(restored.reversed()) }.exceptionOrNull()

        assertInstanceOf(DownloadQueueWriteException::class.java, failure)
        assertEquals(3, attempts)
        assertEquals(previous, active.all)
    }

    private fun animeStoreFactory(sourcesReady: StateFlow<Boolean> = MutableStateFlow(true)): () -> AnimeDownloadStore {
        val sources = mockk<AnimeSourceManager>()
        every { sources.isInitialized } returns sourcesReady
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

        return { AnimeDownloadStore(context, sources, Json, getAnime, getEpisode) }
    }

    @Test
    fun `manga restore preserves pending items before the caller republishes them`() = runTest {
        val active = seed("manga", "mangaId", "chapterId")
        val seeded = active.all
        val sourcesReady = MutableStateFlow(false)
        val sources = mockk<MangaSourceManager>()
        every { sources.isInitialized } returns sourcesReady
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

        val createStore = { MangaDownloadStore(context, sources, Json, getManga, getChapter) }
        val restoring = async(start = CoroutineStart.UNDISPATCHED) { createStore().restore() }

        assertFalse(restoring.isCompleted)
        assertEquals(seeded, active.all)
        sourcesReady.value = true
        assertEquals(ids, restoring.await().map { it.chapter.id })
        assertEquals(ids, createStore().restore().map { it.chapter.id })
    }

    private fun entries(parentField: String, itemField: String): MutableMap<String, Any> {
        val entries = mutableMapOf<String, Any>()
        ids.forEachIndexed { order, id ->
            entries[id.toString()] = """{"$parentField":7,"$itemField":$id,"order":$order}"""
        }
        return entries
    }

    private fun seed(media: String, parentField: String, itemField: String): SharedPreferences {
        val active = preferences("active_downloads_$media", entries(parentField, itemField))
        preferences("active_downloads")
        preferences("active_downloads_migration", mutableMapOf("${media}_active_downloads_migrated" to true))
        return active
    }

    private fun preferences(
        name: String,
        entries: MutableMap<String, Any> = mutableMapOf(),
        commitSucceeds: () -> Boolean = { true },
    ): SharedPreferences {
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
                commitSucceeds()
            }
            editor
        }
        return preferences
    }
}
