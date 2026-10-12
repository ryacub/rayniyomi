package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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

    private val source = mockk<AnimeHttpSource> { every { id } returns SOURCE_ID }
    private val anime = Anime.create().copy(id = 1L, source = SOURCE_ID, title = ANIME_TITLE)

    private val animeDir = mockk<UniFile> {
        every { isDirectory } returns true
        every { name } returns ANIME_TITLE
        every { listFiles() } answers {
            scanStarted.countDown()
            releaseScan.await(10, TimeUnit.SECONDS)
            arrayOf(episodeDir(EXISTING), episodeDir(OTHER))
        }
    }

    private val sourceDir = mockk<UniFile> {
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
        awaitScanSwap()

        isDownloaded(ADDED) shouldBe true
    }

    @Test
    fun `an episode removed during a cache scan stays removed after the scan`() = runBlocking<Unit> {
        startScan()

        runDuringScan { cache.removeEpisode(episode(EXISTING), anime) }
        awaitScanSwap()

        isDownloaded(EXISTING) shouldBe false
    }

    private fun startScan() {
        cache.invalidateCache()
        scanStarted.await(10, TimeUnit.SECONDS) shouldBe true
    }

    private fun awaitScanSwap() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!isDownloaded(OTHER)) {
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

    private fun episodeDir(dirName: String) = mockk<UniFile> {
        every { isDirectory } returns true
        every { name } returns dirName
    }

    private companion object {
        const val SOURCE_ID = 1L
        const val SOURCE_DIR_NAME = "Source"
        const val ANIME_TITLE = "Title"
        const val EXISTING = "Existing"
        const val OTHER = "Other"
        const val ADDED = "Added"
    }
}
