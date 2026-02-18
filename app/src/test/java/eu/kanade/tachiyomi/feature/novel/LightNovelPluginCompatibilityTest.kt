package eu.kanade.tachiyomi.feature.novel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LightNovelPluginCompatibilityTest {

    @Test
    fun `returns compatible when api and host versions match contract`() {
        val result = evaluateLightNovelPluginCompatibility(
            pluginApiVersion = 1,
            minHostVersion = 100,
            targetHostVersion = 200,
            hostVersionCode = 150,
            expectedPluginApiVersion = 1,
        )

        assertEquals(LightNovelPluginCompatibilityResult.COMPATIBLE, result)
    }

    @Test
    fun `returns api mismatch when plugin api differs`() {
        val result = evaluateLightNovelPluginCompatibility(
            pluginApiVersion = 2,
            minHostVersion = 100,
            targetHostVersion = 200,
            hostVersionCode = 150,
            expectedPluginApiVersion = 1,
        )

        assertEquals(LightNovelPluginCompatibilityResult.API_MISMATCH, result)
    }

    @Test
    fun `returns host too old when host is below min`() {
        val result = evaluateLightNovelPluginCompatibility(
            pluginApiVersion = 1,
            minHostVersion = 100,
            targetHostVersion = 200,
            hostVersionCode = 99,
            expectedPluginApiVersion = 1,
        )

        assertEquals(LightNovelPluginCompatibilityResult.HOST_TOO_OLD, result)
    }

    @Test
    fun `returns host too new when host exceeds target`() {
        val result = evaluateLightNovelPluginCompatibility(
            pluginApiVersion = 1,
            minHostVersion = 100,
            targetHostVersion = 200,
            hostVersionCode = 201,
            expectedPluginApiVersion = 1,
        )

        assertEquals(LightNovelPluginCompatibilityResult.HOST_TOO_NEW, result)
    }

    @Test
    fun `returns compatible when target host is not provided`() {
        val result = evaluateLightNovelPluginCompatibility(
            pluginApiVersion = 1,
            minHostVersion = 100,
            targetHostVersion = null,
            hostVersionCode = 999,
            expectedPluginApiVersion = 1,
        )

        assertEquals(LightNovelPluginCompatibilityResult.COMPATIBLE, result)
    }
}
