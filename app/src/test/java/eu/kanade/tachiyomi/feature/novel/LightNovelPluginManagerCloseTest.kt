package eu.kanade.tachiyomi.feature.novel

import android.content.Context
import eu.kanade.domain.novel.NovelFeaturePreferences
import eu.kanade.tachiyomi.network.NetworkHelper
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test

class LightNovelPluginManagerCloseTest {
    private val mockContext = mockk<Context>(relaxed = true)
    private val mockNetwork = mockk<NetworkHelper>(relaxed = true)
    private val mockJson = mockk<Json>(relaxed = true)
    private val mockPreferences = mockk<NovelFeaturePreferences>(relaxed = true)

    @Test
    fun closeCanBeCalledWithoutThrowing() {
        // Arrange
        val pluginManager = LightNovelPluginManager(
            context = mockContext,
            network = mockNetwork,
            json = mockJson,
            preferences = mockPreferences,
        )

        // Act & Assert (no exception should be thrown)
        pluginManager.close()
    }

    @Test
    fun closeIsIdempotent() {
        // Arrange
        val pluginManager = LightNovelPluginManager(
            context = mockContext,
            network = mockNetwork,
            json = mockJson,
            preferences = mockPreferences,
        )

        // Act & Assert (calling close twice should not throw)
        pluginManager.close()
        pluginManager.close() // Second call should also succeed
    }

}
