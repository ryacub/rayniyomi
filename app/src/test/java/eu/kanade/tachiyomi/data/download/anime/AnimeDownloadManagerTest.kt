package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.download.service.DownloadPreferences

class AnimeDownloadManagerTest {

    private lateinit var context: Context
    private lateinit var mockNotifier: AnimeDownloadNotifier
    private lateinit var mockDownloader: AnimeDownloader
    private lateinit var manager: AnimeDownloadManager
    private lateinit var crashCountPref: Preference<Int>
    private var crashCount = 0

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        mockNotifier = mockk(relaxed = true)
        mockDownloader = mockk(relaxed = true) {
            every { start() } returns true
        }

        // InMemoryPreferenceStore creates a new Preference object each call, so set() doesn't
        // persist. Use a shared mock preference that actually tracks state.
        crashCount = 0
        crashCountPref = mockk {
            every { get() } answers { crashCount }
            every { set(any()) } answers { crashCount = firstArg() }
        }
        val downloadPreferences: DownloadPreferences = mockk(relaxed = true) {
            every { animeDownloadJobCrashCount() } returns crashCountPref
        }

        manager = AnimeDownloadManager(
            context = context,
            storageManager = mockk(relaxed = true),
            provider = mockk(relaxed = true),
            cache = mockk(relaxed = true),
            getCategories = mockk(relaxed = true),
            sourceManager = mockk(relaxed = true),
            downloadPreferences = downloadPreferences,
            downloaderForTesting = mockDownloader,
        )
        manager.notifier = mockNotifier
    }

    @Test
    fun `incrementJobCrashCount does not show notification below threshold`() {
        // Two crashes — count reaches 2, below threshold of 3
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()

        verify(exactly = 0) { mockNotifier.onCrashThresholdExceeded() }
    }

    @Test
    fun `incrementJobCrashCount shows notification when threshold is reached`() {
        // Three crashes — count reaches 3 (threshold)
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()

        verify { mockNotifier.onCrashThresholdExceeded() }
    }

    @Test
    fun `incrementJobCrashCount shows notification on each call above threshold`() {
        // Four crashes — fourth is above threshold, should also notify
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()
        manager.incrementJobCrashCount()

        verify(atLeast = 2) { mockNotifier.onCrashThresholdExceeded() }
    }

    @Test
    fun `downloaderStart resets non-zero crash count before start attempt`() {
        crashCount = 3

        val started = manager.downloaderStart()

        assertEquals(true, started)
        assertEquals(0, crashCountPref.get())
        verify(exactly = 1) { mockDownloader.start() }
    }

    @Test
    fun `downloaderStart leaves zero crash count unchanged`() {
        crashCount = 0

        val started = manager.downloaderStart()

        assertEquals(true, started)
        assertEquals(0, crashCountPref.get())
        verify(exactly = 1) { mockDownloader.start() }
    }

    @Test
    fun `downloaderStart keeps reset state when start attempt throws`() {
        crashCount = 2
        every { mockDownloader.start() } throws RuntimeException("start failed")

        assertThrows(RuntimeException::class.java) {
            manager.downloaderStart()
        }

        assertEquals(0, crashCountPref.get())
        verify(exactly = 1) { mockDownloader.start() }
    }
}
