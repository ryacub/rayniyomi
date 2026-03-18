package eu.kanade.tachiyomi.data.download.manga

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.download.service.DownloadPreferences

class MangaDownloadManagerTest {

    private lateinit var context: Context
    private lateinit var mockNotifier: MangaDownloadNotifier
    private lateinit var manager: MangaDownloadManager

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        mockNotifier = mockk(relaxed = true)

        // InMemoryPreferenceStore creates a new Preference object each call, so set() doesn't
        // persist. Use a shared mock preference that actually tracks state.
        var crashCount = 0
        val crashCountPref: Preference<Int> = mockk {
            every { get() } answers { crashCount }
            every { set(any()) } answers { crashCount = firstArg() }
        }
        val downloadPreferences: DownloadPreferences = mockk(relaxed = true) {
            every { mangaDownloadJobCrashCount() } returns crashCountPref
        }

        manager = MangaDownloadManager(
            context = context,
            storageManager = mockk(relaxed = true),
            provider = mockk(relaxed = true),
            cache = mockk(relaxed = true),
            getCategories = mockk(relaxed = true),
            sourceManager = mockk(relaxed = true),
            downloadPreferences = downloadPreferences,
            downloader = mockk(relaxed = true),
            pendingDeleter = mockk(relaxed = true),
            notifier = mockNotifier,
        )
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
}
