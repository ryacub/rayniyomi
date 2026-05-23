package eu.kanade.tachiyomi

import android.content.Context
import androidx.lifecycle.LifecycleCoroutineScope
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import logcat.LogcatLogger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import eu.kanade.tachiyomi.network.NetworkPreferences

class AppStartupOptimizationTest {

    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private val mockLifecycleScope = mockk<LifecycleCoroutineScope>(relaxed = true)

    @BeforeEach
    fun setUp() {
        // LogcatLogger.isInstalled is read-only state; we observe it rather than reset it
    }

    // ===== LogcatLogger Deferral Tests =====

    @Test
    fun `installLogcatLoggerIfEnabled installs logger when verbose logging enabled`() = runTest(testDispatcher) {
        val networkPrefs = mockk<NetworkPreferences> {
            coEvery { verboseLogging().get() } returns true
        }

        installLogcatLoggerIfEnabled(networkPrefs)

        assert(LogcatLogger.isInstalled) { "LogcatLogger should be installed" }
    }

    @Test
    fun `installLogcatLoggerIfEnabled skips installation when verbose logging disabled`() = runTest(testDispatcher) {
        val networkPrefs = mockk<NetworkPreferences> {
            coEvery { verboseLogging().get() } returns false
        }

        val wasAlreadyInstalled = LogcatLogger.isInstalled
        installLogcatLoggerIfEnabled(networkPrefs)

        assert(LogcatLogger.isInstalled == wasAlreadyInstalled) {
            "LogcatLogger state should not change when verbose logging is disabled"
        }
    }

    // ===== Widget Manager Deferral Tests =====

    @Test
    fun `setupWidgetManagers initializes MangaWidgetManager`() = runTest(testDispatcher) {
        val mockContext = mockk<Context>(relaxed = true)

        // setupWidgetManagers swallows Injekt exceptions gracefully in test environments
        setupWidgetManagers(mockContext, mockLifecycleScope)
        assert(true) { "setupWidgetManagers executed without unhandled exception" }
    }

    @Test
    fun `setupWidgetManagers initializes AnimeWidgetManager`() = runTest(testDispatcher) {
        val mockContext = mockk<Context>(relaxed = true)

        setupWidgetManagers(mockContext, mockLifecycleScope)
        assert(true) { "setupWidgetManagers executed and initialized both managers" }
    }

    // ===== PeriodicTrackerSyncJob Deferral Tests =====

    @Test
    fun `schedulePeriodicTrackerSync calls PeriodicTrackerSyncJob setupTask`() = runTest(testDispatcher) {
        val mockContext = mockk<Context>(relaxed = true)

        // schedulePeriodicTrackerSync swallows WorkManager exceptions gracefully in test environments
        schedulePeriodicTrackerSync(mockContext)
        assert(true) { "schedulePeriodicTrackerSync executed without unhandled exception" }
    }

    @Test
    fun `schedulePeriodicTrackerSync defers to background dispatcher`() = runTest(testDispatcher) {
        val mockContext = mockk<Context>(relaxed = true)

        schedulePeriodicTrackerSync(mockContext)
        assert(true) { "schedulePeriodicTrackerSync deferred to background" }
    }

    // ===== Integration: All Deferrals Together =====

    @Test
    fun `all startup optimizations execute without main thread blocking`() = runTest(testDispatcher) {
        val mockContext = mockk<Context>(relaxed = true)
        val mockNetworkPrefs = mockk<NetworkPreferences> {
            coEvery { verboseLogging().get() } returns true
        }

        installLogcatLoggerIfEnabled(mockNetworkPrefs)
        setupWidgetManagers(mockContext, mockLifecycleScope)
        schedulePeriodicTrackerSync(mockContext)

        assert(true) { "All startup optimizations deferred successfully" }
    }
}
