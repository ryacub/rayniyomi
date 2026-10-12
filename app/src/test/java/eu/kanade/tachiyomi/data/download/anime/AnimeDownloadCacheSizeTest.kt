package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.storage.service.StorageManager
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class AnimeDownloadCacheSizeTest {

    @TempDir
    lateinit var cacheDir: File

    private val lengthReads = AtomicInteger()
    private var blockFirstLength: CountDownLatch? = null
    private val firstLengthBlocked = AtomicBoolean(false)
    private val walkStarted = CountDownLatch(1)

    private val source = mockk<AnimeHttpSource> { every { id } returns SOURCE_ID }
    private val anime = Anime.create().copy(id = 1L, source = SOURCE_ID, title = ANIME_TITLE)

    private val episodeFiles = CopyOnWriteArrayList(listOf(mp4(EXISTING, 100), mp4(OTHER, 200)))

    private val animeDir = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns true
        every { name } returns ANIME_TITLE
        every { listFiles() } answers { episodeFiles.toTypedArray() }
    }

    private val sourceDir = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns true
        every { name } returns SOURCE_DIR_NAME
        every { listFiles() } returns arrayOf(animeDir)
    }

    private val rootDir = mockk<UniFile>(relaxed = true) {
        every { listFiles() } returns arrayOf(sourceDir)
    }

    private val cache by lazy { newCache() }

    @AfterEach
    fun tearDown() {
        blockFirstLength?.countDown()
        AppGraphHolder.graphOrNull = null
        unmockkStatic(UniFile::class)
    }

    @Test
    fun `reading a size twice walks the anime folder once`() {
        awaitEpisodeCount(cache, 2)

        cache.getDownloadSize(anime) shouldBe 300
        cache.getDownloadSize(anime) shouldBe 300

        lengthReads.get() shouldBe 2
    }

    @Test
    fun `an episode added after a size read is counted in the next read`() = runBlocking<Unit> {
        awaitEpisodeCount(cache, 2)
        cache.getDownloadSize(anime)

        episodeFiles += mp4(ADDED, 50)
        cache.addEpisode(ADDED, animeDir, anime)

        cache.getDownloadSize(anime) shouldBe 350
    }

    @Test
    fun `an episode removed after a size read is dropped from the next read`() = runBlocking<Unit> {
        awaitEpisodeCount(cache, 2)
        cache.getDownloadSize(anime)

        episodeFiles.removeIf { it.name == "$EXISTING.mp4" }
        cache.removeEpisode(episode(EXISTING), anime)

        cache.getDownloadSize(anime) shouldBe 200
    }

    @Test
    fun `a size walk that overlaps an episode add does not keep its result`() = runBlocking<Unit> {
        awaitEpisodeCount(cache, 2)
        val releaseWalk = CountDownLatch(1).also { blockFirstLength = it }

        val overlappingRead = async(Dispatchers.IO) { cache.getDownloadSize(anime) }
        walkStarted.await(10, TimeUnit.SECONDS) shouldBe true
        episodeFiles += mp4(ADDED, 50)
        cache.addEpisode(ADDED, animeDir, anime)
        releaseWalk.countDown()
        overlappingRead.await()

        cache.getDownloadSize(anime) shouldBe 350
    }

    @Test
    fun `a cache scan drops stored sizes`() {
        awaitEpisodeCount(cache, 2)
        cache.getDownloadSize(anime)

        episodeFiles += mp4(ADDED, 50)
        cache.invalidateCache()
        awaitEpisodeCount(cache, 3)

        cache.getDownloadSize(anime) shouldBe 350
    }

    @Test
    fun `a stored size is read from the index after a restart without a walk`() {
        restoreIndexFoldersAs(animeDir)
        awaitEpisodeCount(cache, 2)
        cache.getDownloadSize(anime)
        Thread.sleep(INDEX_WRITE_WAIT_MS)
        val readsBeforeRestart = lengthReads.get()

        val restarted = newCache(downloadsDir = mockk(relaxed = true))
        awaitEpisodeCount(restarted, 2)

        restarted.getDownloadSize(anime) shouldBe 300
        lengthReads.get() shouldBe readsBeforeRestart
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun `an index written before sizes were stored still loads`() {
        val oldIndex = OldRoot(
            sourceDirs = mapOf(
                SOURCE_ID to OldSource(
                    animeDirs = mapOf(ANIME_TITLE to OldAnime(dir = FOLDER_URI, episodeDirs = setOf(EXISTING, OTHER))),
                ),
            ),
        )
        File(cacheDir, "dl_anime_index_cache_v3").writeBytes(ProtoBuf.encodeToByteArray(oldIndex))
        restoreIndexFoldersAs(animeDir)

        awaitEpisodeCount(cache, 2)

        cache.getDownloadSize(anime) shouldBe 300
        lengthReads.get() shouldBe 2
    }

    @Test
    fun `a partial download folder is not counted`() {
        episodeFiles += partialDownload("${ADDED}_tmp", 1_000)
        awaitEpisodeCount(cache, 2)

        cache.getDownloadSize(anime) shouldBe 300
    }

    private fun newCache(downloadsDir: UniFile = rootDir) = AnimeDownloadCache(
        context = mockk<Context> { every { cacheDir } returns this@AnimeDownloadCacheSizeTest.cacheDir },
        provider = mockk {
            every { getSourceDirName(any()) } returns SOURCE_DIR_NAME
            every { getAnimeDirName(any()) } answers { firstArg() }
            every { getValidEpisodeDirNames(any(), any()) } answers { listOf(firstArg()) }
            every { findSourceDir(any()) } returns sourceDir
        },
        sourceManager = mockk<AnimeSourceManager> {
            every { isInitialized } returns MutableStateFlow(true)
            every { getOnlineSources() } returns listOf(source)
            every { getStubSources() } returns emptyList()
            every { get(SOURCE_ID) } returns source
        },
        extensionManager = mockk<AnimeExtensionManager> {
            every { isInitialized } returns MutableStateFlow(true)
        },
        storageManager = mockk<StorageManager> {
            every { getDownloadsDirectory() } returns downloadsDir
            every { changes } returns MutableSharedFlow()
        },
    )

    private fun restoreIndexFoldersAs(dir: UniFile) {
        AppGraphHolder.graphOrNull = mockk<AppGraph> { every { application } returns mockk() }
        mockkStatic(UniFile::class)
        every { UniFile.fromUri(any(), any()) } returns dir
    }

    private fun awaitEpisodeCount(target: AnimeDownloadCache, expected: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (target.getDownloadCount(anime) != expected) {
            check(System.nanoTime() < deadline) { "The cache never held $expected episodes" }
            Thread.sleep(10)
        }
    }

    private fun mp4(episodeName: String, size: Long) = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns false
        every { isFile } returns true
        every { name } returns "$episodeName.mp4"
        every { length() } answers {
            lengthReads.incrementAndGet()
            blockFirstLength?.let { latch ->
                if (firstLengthBlocked.compareAndSet(false, true)) {
                    walkStarted.countDown()
                    latch.await(10, TimeUnit.SECONDS)
                }
            }
            size
        }
    }

    private fun partialDownload(dirName: String, size: Long) = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns true
        every { name } returns dirName
        every { listFiles() } returns arrayOf(mp4("partial", size))
    }

    private fun episode(episodeName: String) = Episode.create().copy(name = episodeName, scanlator = null)

    @Serializable
    private class OldRoot(val dir: String? = null, val sourceDirs: Map<Long, OldSource> = mapOf())

    @Serializable
    private class OldSource(val dir: String? = null, val animeDirs: Map<String, OldAnime> = mapOf())

    @Serializable
    private class OldAnime(val dir: String? = null, val episodeDirs: Set<String> = setOf())

    private companion object {
        const val SOURCE_ID = 1L
        const val SOURCE_DIR_NAME = "Source"
        const val ANIME_TITLE = "Title"
        const val EXISTING = "Existing"
        const val OTHER = "Other"
        const val ADDED = "Added"
        const val FOLDER_URI = "content://folder"
        const val INDEX_WRITE_WAIT_MS = 2_000L
    }
}
