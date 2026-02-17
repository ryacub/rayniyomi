package eu.kanade.tachiyomi.ui.player.utils

import eu.kanade.tachiyomi.animesource.model.ChapterType
import eu.kanade.tachiyomi.animesource.model.TimeStamp
import eu.kanade.tachiyomi.ui.player.shouldIntegrateAniSkipOnFileLoad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.track.anime.model.AnimeTrack
import java.io.File
import kotlin.io.path.createTempDirectory

class AniSkipGapClosureTest {

    @Test
    fun `cache hit returns stored timestamps within ttl`() {
        val tmpDir = createTempDirectory(prefix = "aniskip_cache_test").toFile()
        val now = 1_000_000L
        val cache = AniSkipDiskCache(
            cacheDir = tmpDir,
            ttlMs = 7_000L,
            nowProvider = { now },
        )
        val timestamps = listOf(TimeStamp(start = 10.0, end = 30.0, name = "Opening", type = ChapterType.Opening))

        cache.put(malId = 1L, episodeNumber = 1, episodeLength = 1_500L, timestamps = timestamps)

        val cached = cache.get(malId = 1L, episodeNumber = 1, episodeLength = 1_500L)
        assertNotNull(cached)
        assertEquals(timestamps, cached)
        tmpDir.deleteRecursively()
    }

    @Test
    fun `cache miss returns null when no file exists`() {
        val tmpDir = createTempDirectory(prefix = "aniskip_cache_test").toFile()
        val cache = AniSkipDiskCache(cacheDir = tmpDir)

        assertNull(cache.get(malId = 999L, episodeNumber = 5, episodeLength = 1_500L))
        tmpDir.deleteRecursively()
    }

    @Test
    fun `expired cache entry returns null and is deleted`() {
        val tmpDir = createTempDirectory(prefix = "aniskip_cache_test").toFile()
        var now = 1_000_000L
        val cache = AniSkipDiskCache(
            cacheDir = tmpDir,
            ttlMs = 100L,
            nowProvider = { now },
        )
        val timestamps = listOf(TimeStamp(start = 10.0, end = 30.0, name = "Opening", type = ChapterType.Opening))
        cache.put(malId = 1L, episodeNumber = 1, episodeLength = 1_500L, timestamps = timestamps)

        now += 101L
        val cached = cache.get(malId = 1L, episodeNumber = 1, episodeLength = 1_500L)
        assertNull(cached)

        val cacheFile = File(tmpDir, "aniskip_1_1_${roundedEpisodeLength(1_500L)}.json")
        assertFalse(cacheFile.exists())
        tmpDir.deleteRecursively()
    }

    @Test
    fun `malformed cache entry falls back safely`() {
        val tmpDir = createTempDirectory(prefix = "aniskip_cache_test").toFile()
        val cacheFile = File(tmpDir, "aniskip_1_1_${roundedEpisodeLength(1_500L)}.json")
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText("not valid json")

        val cache = AniSkipDiskCache(cacheDir = tmpDir)
        val cached = cache.get(malId = 1L, episodeNumber = 1, episodeLength = 1_500L)
        assertNull(cached)
        assertFalse(cacheFile.exists())
        tmpDir.deleteRecursively()
    }

    @Test
    fun `resolve mal id prefers mal tracker when present`() {
        val tracks = listOf(
            animeTrack(trackerId = 2L, remoteId = 200L),
            animeTrack(trackerId = 1L, remoteId = 100L),
        )

        val malId = resolveMalId(
            tracks = tracks,
            trackerKindForId = {
                when (it) {
                    1L -> AniSkipTrackerKind.MAL
                    2L -> AniSkipTrackerKind.ANILIST
                    else -> AniSkipTrackerKind.OTHER
                }
            },
            anilistToMalResolver = { 999L },
        )

        assertEquals(100L, malId)
    }

    @Test
    fun `resolve mal id falls back to anilist mapping`() {
        val tracks = listOf(animeTrack(trackerId = 2L, remoteId = 555L))

        val malId = resolveMalId(
            tracks = tracks,
            trackerKindForId = {
                if (it == 2L) AniSkipTrackerKind.ANILIST else AniSkipTrackerKind.OTHER
            },
            anilistToMalResolver = { 777L },
        )

        assertEquals(777L, malId)
    }

    @Test
    fun `resolve mal id returns null when no valid tracker exists`() {
        val tracks = listOf(animeTrack(trackerId = 3L, remoteId = 111L))
        val malId = resolveMalId(
            tracks = tracks,
            trackerKindForId = { AniSkipTrackerKind.OTHER },
            anilistToMalResolver = { 0L },
        )
        assertNull(malId)
    }

    @Test
    fun `segment skip selection respects opening and ending toggles`() {
        assertTrue(
            shouldAutoSkipSegment(
                chapterType = ChapterType.Opening,
                autoSkipOpening = true,
                autoSkipEnding = false,
            ),
        )
        assertFalse(
            shouldAutoSkipSegment(
                chapterType = ChapterType.Ending,
                autoSkipOpening = true,
                autoSkipEnding = false,
            ),
        )
        assertTrue(
            shouldAutoSkipSegment(
                chapterType = ChapterType.Recap,
                autoSkipOpening = false,
                autoSkipEnding = true,
            ),
        )
        assertFalse(
            shouldAutoSkipSegment(
                chapterType = ChapterType.Other,
                autoSkipOpening = true,
                autoSkipEnding = true,
            ),
        )
    }

    @Test
    fun `aniSkip integration gate respects existing behavior`() {
        assertFalse(
            shouldIntegrateAniSkipOnFileLoad(
                aniSkipEnabled = false,
                introSkipEnabled = true,
                disableAniSkipOnChapters = false,
                hasExistingChapters = false,
            ),
        )
        assertFalse(
            shouldIntegrateAniSkipOnFileLoad(
                aniSkipEnabled = true,
                introSkipEnabled = true,
                disableAniSkipOnChapters = true,
                hasExistingChapters = true,
            ),
        )
        assertTrue(
            shouldIntegrateAniSkipOnFileLoad(
                aniSkipEnabled = true,
                introSkipEnabled = true,
                disableAniSkipOnChapters = true,
                hasExistingChapters = false,
            ),
        )
    }

    private fun animeTrack(
        trackerId: Long,
        remoteId: Long,
    ): AnimeTrack {
        return AnimeTrack(
            id = 0L,
            animeId = 1L,
            trackerId = trackerId,
            remoteId = remoteId,
            libraryId = null,
            title = "",
            lastEpisodeSeen = 0.0,
            totalEpisodes = 0L,
            status = 0L,
            score = 0.0,
            remoteUrl = "",
            startDate = 0L,
            finishDate = 0L,
            private = false,
        )
    }
}
