package eu.kanade.tachiyomi.feature.novel

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import eu.kanade.domain.novel.NovelFeaturePreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class LightNovelPluginLauncherTest {

    private lateinit var pluginReadiness: LightNovelPluginReadiness

    /** Collects intents passed to the activityStarter. */
    private val launchedIntents = mutableListOf<Intent>()

    /** A mock Intent pre-configured with the expected ComponentName. */
    private lateinit var mockLibraryIntent: Intent

    @BeforeEach
    fun setUp() {
        pluginReadiness = mockk()
        launchedIntents.clear()

        val mockComponent = mockk<ComponentName>(relaxed = true)
        every { mockComponent.packageName } returns LightNovelPluginManager.PLUGIN_PACKAGE_NAME
        every { mockComponent.className } returns "${LightNovelPluginManager.PLUGIN_PACKAGE_NAME}.MainActivity"

        mockLibraryIntent = mockk(relaxed = true)
        every { mockLibraryIntent.component } returns mockComponent
    }

    // -------------------------------------------------------------------------
    // getAvailability tests
    // -------------------------------------------------------------------------

    @Test
    fun `getAvailability_whenGateOff_returnsFalse`() {
        val launcher = createLauncher(enabled = false, pluginReady = true)

        assertFalse(launcher.isAvailable())
    }

    @Test
    fun `getAvailability_whenGateOnAndReady_returnsTrue`() {
        val launcher = createLauncher(enabled = true, pluginReady = true)

        assertTrue(launcher.isAvailable())
    }

    // -------------------------------------------------------------------------
    // launch tests
    // -------------------------------------------------------------------------

    @Test
    fun `launch_whenFeatureGateOff_doesNotLaunchIntent`() {
        val launcher = createLauncher(enabled = false, pluginReady = true)

        val result = launcher.launchLibrary()

        assertFalse(result)
        assertTrue(launchedIntents.isEmpty(), "Expected no intents to be launched")
    }

    @Test
    fun `launch_whenGateOnButPluginNotReady_doesNotLaunchIntent`() {
        val launcher = createLauncher(enabled = true, pluginReady = false)

        val result = launcher.launchLibrary()

        assertFalse(result)
        assertTrue(launchedIntents.isEmpty(), "Expected no intents to be launched")
    }

    @Test
    fun `launch_whenGateOnAndPluginReady_launchesMainActivityIntent`() {
        val launcher = createLauncher(enabled = true, pluginReady = true)

        val result = launcher.launchLibrary()

        assertTrue(result)
        assertTrue(launchedIntents.size == 1, "Expected exactly one intent to be launched")
        val capturedIntent = launchedIntents.first()
        assertTrue(
            capturedIntent.component?.packageName == LightNovelPluginManager.PLUGIN_PACKAGE_NAME,
            "Intent should target the plugin package",
        )
        assertTrue(
            capturedIntent.component?.className?.endsWith("MainActivity") == true,
            "Intent should target MainActivity",
        )
    }

    @Test
    fun `launch_whenActivityNotFound_returnsFalseWithoutCrash`() {
        val throwingStarter: (Intent) -> Unit = { throw ActivityNotFoundException("not found") }
        val launcher = createLauncher(enabled = true, pluginReady = true, starter = throwingStarter)

        val result = launcher.launchLibrary()

        assertFalse(result)
    }

    @Test
    fun `launch_whenSecurityException_returnsFalseWithoutCrash`() {
        val throwingStarter: (Intent) -> Unit = { throw SecurityException("blocked") }
        val launcher = createLauncher(enabled = true, pluginReady = true, starter = throwingStarter)

        val result = launcher.launchLibrary()

        assertFalse(result)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun createLauncher(
        enabled: Boolean,
        pluginReady: Boolean,
        starter: (Intent) -> Unit = { launchedIntents.add(it) },
    ): LightNovelPluginLauncher {
        val preferences = NovelFeaturePreferences(
            InMemoryPreferenceStore(
                sequenceOf(
                    InMemoryPreferenceStore.InMemoryPreference(
                        key = "enable_light_novels",
                        data = enabled,
                        defaultValue = false,
                    ),
                ),
            ),
        )
        every { pluginReadiness.isPluginReady() } returns pluginReady
        val gate = LightNovelFeatureGate(preferences, pluginReadiness)
        val context = mockk<android.content.Context>(relaxed = true)
        return LightNovelPluginLauncher(
            context = context,
            featureGate = gate,
            activityStarter = starter,
            libraryIntentFactory = { mockLibraryIntent },
        )
    }
}
