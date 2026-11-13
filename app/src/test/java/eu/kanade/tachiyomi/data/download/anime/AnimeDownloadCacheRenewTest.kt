package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.di.AppGraph
import eu.kanade.tachiyomi.di.AppGraphHolder
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

class AnimeDownloadCacheRenewTest {

    @TempDir
    lateinit var cacheDir: File

    private val scanStarted = CountDownLatch(1)
    private val releaseScan = CountDownLatch(1)

    @Volatile
    private var episodeDirNames = listOf(EXISTING, OTHER)

    private val source = mockk<AnimeHttpSource> { every { id } returns SOURCE_ID }
    private val anime = Anime.create().copy(id = 1L, source = SOURCE_ID, title = ANIME_TITLE)

    private val animeDir = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns true
        every { name } returns ANIME_TITLE
        every { listFiles() } answers {
            val listedNames = episodeDirNames
            scanStarted.countDown()
            releaseScan.await(10, TimeUnit.SECONDS)
            listedNames.map(::episodeDir).toTypedArray()
        }
    }

    private val sourceDir = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns true
        every { name } returns SOURCE_DIR_NAME
        every { listFiles() } returns arrayOf(animeDir)
    }

    private val rootDir = mockk<UniFile>(relaxed = true) {
        every { listFiles() } returns arrayOf(sourceDir)
    }

    private val cache by lazy {
        AnimeDownloadCache(
            context = mockk<Context> { every { cacheDir } returns this@AnimeDownloadCacheRenewTest.cacheDir },
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
                every { getDownloadsDirectory() } returns rootDir
                every { changes } returns MutableSharedFlow()
            },
        )
    }

    @AfterEach
    fun tearDown() {
        releaseScan.countDown()
        AppGraphHolder.graphOrNull = null
        unmockkStatic(UniFile::class)
    }

    @Test
    fun `adding an episode does not wait for a running cache scan`() = runBlocking<Unit> {
        startScan()

        val added = withTimeoutOrNull(2.seconds) {
            cache.addEpisode(ADDED, animeDir, anime)
        }

        added.shouldNotBeNull()
    }

    @Test
    fun `an episode added during a cache scan stays in the cache after the scan`() = runBlocking<Unit> {
        startScan()

        runDuringScan { cache.addEpisode(ADDED, animeDir, anime) }
        awaitDownloaded(OTHER)

        isDownloaded(ADDED) shouldBe true
    }

    @Test
    fun `an episode removed during a cache scan stays removed after the scan`() = runBlocking<Unit> {
        startScan()

        runDuringScan { cache.removeEpisode(episode(EXISTING), anime) }
        awaitDownloaded(OTHER)

        isDownloaded(EXISTING) shouldBe false
    }

    @Test
    fun `an invalidation during a cache scan rescans the downloads folder`() {
        startScan()

        episodeDirNames = listOf(EXISTING, ADDED)
        cache.invalidateCache()
        releaseScan.countDown()

        awaitDownloaded(ADDED)
    }

    @Test
    fun `an invalidation while the disk index loads rescans the downloads folder`() {
        writeDiskIndex(listOf(STALE))
        val loadStarted = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        restoreIndexFoldersAfter(loadStarted, releaseLoad)
        releaseScan.countDown()

        isDownloaded(EXISTING) shouldBe false
        loadStarted.await(10, TimeUnit.SECONDS) shouldBe true
        cache.invalidateCache()
        releaseLoad.countDown()

        awaitDownloaded(EXISTING)
    }

    @Test
    fun `a read while the disk index loads keeps the loaded index`() {
        writeDiskIndex(listOf(STALE))
        val loadStarted = CountDownLatch(1)
        val releaseLoad = CountDownLatch(1)
        restoreIndexFoldersAfter(loadStarted, releaseLoad)

        isDownloaded(STALE) shouldBe false
        loadStarted.await(10, TimeUnit.SECONDS) shouldBe true
        releaseLoad.countDown()

        awaitDownloaded(STALE)
        scanStarted.await(1, TimeUnit.SECONDS) shouldBe false
    }

    private fun startScan() {
        cache.invalidateCache()
        scanStarted.await(10, TimeUnit.SECONDS) shouldBe true
    }

    private fun awaitDownloaded(episodeName: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!isDownloaded(episodeName)) {
            check(System.nanoTime() < deadline) { "The cache scan did not finish" }
            Thread.sleep(10)
        }
    }

    private fun isDownloaded(episodeName: String) =
        cache.isEpisodeDownloaded(episodeName, null, ANIME_TITLE, SOURCE_ID, skipCache = false)

    private suspend fun CoroutineScope.runDuringScan(mutation: suspend () -> Unit) {
        val job = launch(Dispatchers.IO) { mutation() }
        withTimeoutOrNull(2.seconds) { job.join() }
        releaseScan.countDown()
        job.join()
    }

    private fun episode(episodeName: String) = Episode.create().copy(name = episodeName, scanlator = null)

    private fun episodeDir(dirName: String) = mockk<UniFile>(relaxed = true) {
        every { isDirectory } returns true
        every { name } returns dirName
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun writeDiskIndex(episodeDirNames: List<String>) {
        val index = IndexRoot(
            sourceDirs = mapOf(
                SOURCE_ID to IndexSource(
                    animeDirs = mapOf(
                        ANIME_TITLE to IndexAnime(dir = FOLDER_URI, episodeDirs = episodeDirNames.toSet()),
                    ),
                ),
            ),
        )
        File(cacheDir, "dl_anime_index_cache_v3").writeBytes(ProtoBuf.encodeToByteArray(index))
    }

    private fun restoreIndexFoldersAfter(loadStarted: CountDownLatch, releaseLoad: CountDownLatch) {
        AppGraphHolder.graphOrNull = mockk<AppGraph> { every { application } returns mockk() }
        mockkStatic(UniFile::class)
        every { UniFile.fromUri(any(), any()) } answers {
            loadStarted.countDown()
            releaseLoad.await(10, TimeUnit.SECONDS)
            animeDir
        }
    }

    @Serializable
    private class IndexRoot(val dir: String? = null, val sourceDirs: Map<Long, IndexSource> = mapOf())

    @Serializable
    private class IndexSource(val dir: String? = null, val animeDirs: Map<String, IndexAnime> = mapOf())

    @Serializable
    private class IndexAnime(val dir: String? = null, val episodeDirs: Set<String> = setOf())

    private companion object {
        const val SOURCE_ID = 1L
        const val SOURCE_DIR_NAME = "Source"
        const val ANIME_TITLE = "Title"
        const val EXISTING = "Existing"
        const val OTHER = "Other"
        const val ADDED = "Added"
        const val STALE = "Stale"
        const val FOLDER_URI = "content://folder"
    }
}
