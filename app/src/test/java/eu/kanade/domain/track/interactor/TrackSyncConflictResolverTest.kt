package eu.kanade.domain.track.interactor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.domain.track.manga.model.MangaTrack

class TrackSyncConflictResolverTest {

    private val resolver = TrackSyncConflictResolver()

    @Test
    fun `resolveManga uses remote when remote progress is higher`() {
        val local = mangaTrack(lastChapterRead = 3.0, status = 1L)
        val remote = mangaTrack(lastChapterRead = 8.0, status = 2L, private = true)
        val chapters = listOf(
            chapter(id = 1, number = 1.0, read = true),
            chapter(id = 2, number = 2.0, read = false),
            chapter(id = 3, number = 8.0, read = false),
        )

        val result = resolver.resolveManga(local, remote, chapters)

        assertEquals(8.0, result.mergedTrack.lastChapterRead)
        assertEquals(2L, result.mergedTrack.status)
        assertEquals(true, result.mergedTrack.private)
        assertNull(result.pushRemoteTrack)
        assertEquals(2, result.chapterUpdates.size)
    }

    @Test
    fun `resolveManga prepares remote push when local progress is higher`() {
        val local = mangaTrack(lastChapterRead = 10.0, status = 1L)
        val remote = mangaTrack(lastChapterRead = 4.0, status = 2L, private = false)

        val result = resolver.resolveManga(local, remote, emptyList())

        assertEquals(10.0, result.mergedTrack.lastChapterRead)
        assertEquals(1L, result.mergedTrack.status)
        assertNotNull(result.pushRemoteTrack)
        assertEquals(10.0, result.pushRemoteTrack!!.lastChapterRead)
    }

    @Test
    fun `resolveAnime is no-op push when progress equal`() {
        val local = animeTrack(lastEpisodeSeen = 6.0)
        val remote = animeTrack(lastEpisodeSeen = 6.0)

        val result = resolver.resolveAnime(local, remote, emptyList())

        assertEquals(6.0, result.mergedTrack.lastEpisodeSeen)
        assertNull(result.pushRemoteTrack)
        assertEquals(0, result.episodeUpdates.size)
    }

    @Test
    fun `resolveAnime marks episodes seen up to winning progress`() {
        val local = animeTrack(lastEpisodeSeen = 1.0)
        val remote = animeTrack(lastEpisodeSeen = 3.0)
        val episodes = listOf(
            episode(id = 1, number = 1.0, seen = true),
            episode(id = 2, number = 2.0, seen = false),
            episode(id = 3, number = 3.0, seen = false),
            episode(id = 4, number = 4.0, seen = false),
        )

        val result = resolver.resolveAnime(local, remote, episodes)

        assertEquals(3.0, result.mergedTrack.lastEpisodeSeen)
        assertEquals(2, result.episodeUpdates.size)
    }

    private fun mangaTrack(
        lastChapterRead: Double,
        status: Long = 1L,
        private: Boolean = false,
    ): MangaTrack {
        return MangaTrack(
            id = 1,
            mangaId = 10,
            trackerId = 1,
            remoteId = 11,
            libraryId = 12,
            title = "Test Manga",
            lastChapterRead = lastChapterRead,
            totalChapters = 100,
            status = status,
            score = 5.0,
            remoteUrl = "https://example.com/manga",
            startDate = 0,
            finishDate = 0,
            private = private,
        )
    }

    private fun animeTrack(lastEpisodeSeen: Double): AnimeTrack {
        return AnimeTrack(
            id = 1,
            animeId = 20,
            trackerId = 1,
            remoteId = 21,
            libraryId = 22,
            title = "Test Anime",
            lastEpisodeSeen = lastEpisodeSeen,
            totalEpisodes = 24,
            status = 1,
            score = 5.0,
            remoteUrl = "https://example.com/anime",
            startDate = 0,
            finishDate = 0,
            private = false,
        )
    }

    private fun chapter(id: Long, number: Double, read: Boolean): Chapter {
        return Chapter(
            id = id,
            mangaId = 10,
            read = read,
            bookmark = false,
            lastPageRead = 0,
            dateFetch = 0,
            sourceOrder = id,
            url = "chapter-$id",
            name = "Chapter $id",
            dateUpload = 0,
            chapterNumber = number,
            scanlator = null,
            lastModifiedAt = 0,
            version = 1,
        )
    }

    private fun episode(id: Long, number: Double, seen: Boolean): Episode {
        return Episode(
            id = id,
            animeId = 20,
            seen = seen,
            bookmark = false,
            fillermark = false,
            lastSecondSeen = 0,
            totalSeconds = 0,
            dateFetch = 0,
            sourceOrder = id,
            url = "episode-$id",
            name = "Episode $id",
            dateUpload = 0,
            episodeNumber = number,
            scanlator = null,
            summary = null,
            previewUrl = null,
            lastModifiedAt = 0,
            version = 1,
        )
    }
}
