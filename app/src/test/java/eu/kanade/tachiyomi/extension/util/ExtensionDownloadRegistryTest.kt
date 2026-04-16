package eu.kanade.tachiyomi.extension.util

import eu.kanade.tachiyomi.extension.InstallStep
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class ExtensionDownloadRegistryTest {

    @Test
    fun `getOrCreate starts download once for concurrent same package calls`() = runTest {
        val registry = ExtensionDownloadRegistry()
        val starts = AtomicInteger(0)
        val ids = AtomicLong(100)

        val jobs = List(20) {
            async {
                registry.getOrCreate("pkg.test") {
                    starts.incrementAndGet()
                    ids.incrementAndGet()
                }
            }
        }

        val results = jobs.awaitAll()
        val firstId = results.first().downloadId

        assertEquals(1, starts.get(), "Only one download should be created for a package")
        assertTrue(results.all { it.downloadId == firstId }, "All callers should share same active download")
        assertTrue(registry.containsDownloadId(firstId))
    }

    @Test
    fun `removeIfMatch does not remove when expected id mismatches`() {
        val registry = ExtensionDownloadRegistry()
        val active = registry.getOrCreate("pkg.test") { 101L }

        val removed = registry.removeIfMatch("pkg.test", expectedId = 999L)

        assertEquals(null, removed)
        assertTrue(registry.containsDownloadId(active.downloadId))
        assertFalse(registry.isEmpty())
    }

    @Test
    fun `removeIfMatch removes when expected id matches`() {
        val registry = ExtensionDownloadRegistry()
        val active = registry.getOrCreate("pkg.test") { 101L }

        val removed = registry.removeIfMatch("pkg.test", expectedId = active.downloadId)

        assertNotNull(removed)
        assertEquals(active.downloadId, removed?.downloadId)
        assertFalse(registry.containsDownloadId(active.downloadId))
        assertTrue(registry.isEmpty())
    }

    @Test
    fun `updateInstallStep updates flow value for active id`() {
        val registry = ExtensionDownloadRegistry()
        val active = registry.getOrCreate("pkg.test") { 101L }

        registry.updateInstallStep(active.downloadId, InstallStep.Downloading)

        assertEquals(InstallStep.Downloading, active.stateFlow.value)
    }
}
