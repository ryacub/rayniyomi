package eu.kanade.domain.source.novel

import eu.kanade.tachiyomi.feature.novel.LightNovelPluginManager
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class LightNovelPluginHealthCheckerTest {

    private val pluginManager: LightNovelPluginManager = mockk()
    private val checker = LightNovelPluginHealthChecker(pluginManager)

    private val testSourceId = 1L

    // --- shouldSkip ---

    @Test
    fun `shouldSkip always returns false`() {
        val result = checker.shouldSkip(testSourceId)

        result shouldBe false
    }

    @Test
    fun `shouldSkip returns false for any source ID`() {
        listOf(0L, 1L, Long.MAX_VALUE).forEach { id ->
            checker.shouldSkip(id) shouldBe false
        }
    }

    // --- probe ---

    @Test
    fun `probe completes without throwing when plugin is ready`() = runTest {
        every { pluginManager.isPluginReady() } returns true

        checker.probe(testSourceId) // should not throw
    }

    @Test
    fun `probe throws IllegalStateException when plugin is not ready`() = runTest {
        every { pluginManager.isPluginReady() } returns false

        var threw = false
        try {
            checker.probe(testSourceId)
        } catch (e: IllegalStateException) {
            threw = true
        }

        threw shouldBe true
    }
}
