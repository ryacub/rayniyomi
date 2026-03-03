package eu.kanade.tachiyomi.feature.novel

import android.content.Intent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LightNovelPluginStateManagerTest {

    @Test
    fun `returns true for plugin package added`() {
        assertTrue(
            isLightNovelPluginPackageChange(
                action = Intent.ACTION_PACKAGE_ADDED,
                packageName = LightNovelPluginManager.PLUGIN_PACKAGE_NAME,
            ),
        )
    }

    @Test
    fun `returns true for plugin package removed`() {
        assertTrue(
            isLightNovelPluginPackageChange(
                action = Intent.ACTION_PACKAGE_REMOVED,
                packageName = LightNovelPluginManager.PLUGIN_PACKAGE_NAME,
            ),
        )
    }

    @Test
    fun `returns false for different package`() {
        assertFalse(
            isLightNovelPluginPackageChange(
                action = Intent.ACTION_PACKAGE_ADDED,
                packageName = "com.example.other",
            ),
        )
    }

    @Test
    fun `returns false for unrelated action`() {
        assertFalse(
            isLightNovelPluginPackageChange(
                action = Intent.ACTION_PACKAGE_CHANGED,
                packageName = LightNovelPluginManager.PLUGIN_PACKAGE_NAME,
            ),
        )
    }
}
