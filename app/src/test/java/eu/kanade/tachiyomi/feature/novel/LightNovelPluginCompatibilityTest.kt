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

    @Test
    fun `treats target host version 0 as unbounded`() {
        val result = evaluateLightNovelPluginCompatibility(
            pluginApiVersion = 1,
            minHostVersion = 100,
            targetHostVersion = 0,
            hostVersionCode = 999,
            expectedPluginApiVersion = 1,
        )

        assertEquals(LightNovelPluginCompatibilityResult.COMPATIBLE, result)
    }

    @Test
    fun `treats negative target host version as unbounded`() {
        val result = evaluateLightNovelPluginCompatibility(
            pluginApiVersion = 1,
            minHostVersion = 100,
            targetHostVersion = -1,
            hostVersionCode = 999,
            expectedPluginApiVersion = 1,
        )

        assertEquals(LightNovelPluginCompatibilityResult.COMPATIBLE, result)
    }

    @Test
    fun `returns compatible when host equals minimum host version`() {
        val result = evaluateLightNovelPluginCompatibility(
            pluginApiVersion = 1,
            minHostVersion = 100,
            targetHostVersion = 200,
            hostVersionCode = 100,
            expectedPluginApiVersion = 1,
        )

        assertEquals(LightNovelPluginCompatibilityResult.COMPATIBLE, result)
    }

    @Test
    fun `returns compatible when host equals target host version`() {
        val result = evaluateLightNovelPluginCompatibility(
            pluginApiVersion = 1,
            minHostVersion = 100,
            targetHostVersion = 200,
            hostVersionCode = 200,
            expectedPluginApiVersion = 1,
        )

        assertEquals(LightNovelPluginCompatibilityResult.COMPATIBLE, result)
    }

    // normalizeTargetHostVersion tests

    @Test
    fun `normalizeTargetHostVersion returns null for null input`() {
        val result = normalizeTargetHostVersion(null)
        assertEquals(null, result)
    }

    @Test
    fun `normalizeTargetHostVersion returns null for zero`() {
        val result = normalizeTargetHostVersion(0L)
        assertEquals(null, result)
    }

    @Test
    fun `normalizeTargetHostVersion returns null for negative values`() {
        assertEquals(null, normalizeTargetHostVersion(-1L))
        assertEquals(null, normalizeTargetHostVersion(-100L))
    }

    @Test
    fun `normalizeTargetHostVersion returns value for positive numbers`() {
        assertEquals(1L, normalizeTargetHostVersion(1L))
        assertEquals(100L, normalizeTargetHostVersion(100L))
        assertEquals(9999L, normalizeTargetHostVersion(9999L))
    }
}
