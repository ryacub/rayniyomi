package eu.kanade.tachiyomi.extension.anime

import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Tests the real [AnimeExtensionManager.mapExtensions] helper. The mapping must run on
 * the injected worker dispatcher, and rapid source-map replacement must conflate to the
 * latest value so install-replace events cannot surface duplicate or dropped entries.
 */
class AnimeExtensionManagerMapExtensionsTest {

    private val mapThreadName = "r895-map-worker"
    private val scopes = mutableListOf<CoroutineScope>()
    private lateinit var mapDispatcher: ExecutorCoroutineDispatcher

    @BeforeEach
    fun setUp() {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, mapThreadName) }
        mapDispatcher = executor.asCoroutineDispatcher()
    }

    @AfterEach
    fun tearDown() {
        scopes.forEach { it.cancel() }
        scopes.clear()
        mapDispatcher.close()
    }

    @Test
    fun `mapping work runs on the injected dispatcher`() = runTest {
        val accessThreads = mutableListOf<String>()
        val extension = untrusted("pkg1")
        // The seed value read inside stateIn runs synchronously on the test thread
        // and is not part of the assertion.
        val source = MutableStateFlow(recordingMap(mapOf("pkg1" to extension), accessThreads))
        val scope = managerScope()

        val mapped = source.mapExtensions(scope, mapDispatcher)
        val seedAccessCount = accessThreads.size
        scope.launch { mapped.collect { /* drain */ } }
        advanceUntilIdle()

        // flowOn hops to a real worker thread that the test scheduler cannot see,
        // so wait on the wall clock until the mapping work is recorded.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (accessThreads.size == seedAccessCount && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }

        assertEquals(listOf(extension), mapped.value)
        assertTrue(accessThreads.size > seedAccessCount, "Mapping never ran during collection")
        val postSeedAccesses = accessThreads.drop(seedAccessCount)
        assertTrue(
            postSeedAccesses.all { it.startsWith(mapThreadName) },
            "Mapping work ran on $postSeedAccesses instead of $mapThreadName",
        )
    }

    @Test
    fun `rapid replacement conflates to one entry with the latest value`() = runTest {
        val v1 = untrusted("pkg")
        val v2 = v1.copy(versionName = "2.0", versionCode = 2)
        val v3 = v1.copy(versionName = "3.0", versionCode = 3)
        val source = MutableStateFlow(mapOf("pkg" to v1))
        val mapped = source.mapExtensions(managerScope(), UnconfinedTestDispatcher(testScheduler))
        val scope = managerScope()
        scope.launch { mapped.collect { /* drain */ } }
        advanceUntilIdle()

        source.value = source.value + ("pkg" to v2)
        source.value = source.value + ("pkg" to v3)
        advanceUntilIdle()

        assertEquals(listOf(v3), mapped.value)
    }

    /**
     * A scope shaped like [AnimeExtensionManager.scope]: a supervisor job over a
     * standard test dispatcher, with no interceptor of its own beyond the dispatcher.
     */
    private fun TestScope.managerScope(): CoroutineScope {
        return CoroutineScope(
            SupervisorJob() +
                StandardTestDispatcher(testScheduler) +
                CoroutineExceptionHandler { _, _ -> },
        ).also { scopes += it }
    }

    private fun recordingMap(
        delegate: Map<String, AnimeExtension.Untrusted>,
        accessThreads: MutableList<String>,
    ): Map<String, AnimeExtension.Untrusted> {
        return object : AbstractMap<String, AnimeExtension.Untrusted>() {
            override val entries: Set<Map.Entry<String, AnimeExtension.Untrusted>>
                get() {
                    accessThreads += Thread.currentThread().name
                    return delegate.entries
                }

            // Delegates directly so equality checks in the sharing pipeline do not
            // touch the entries getter and pollute the recorded threads.
            override fun equals(other: Any?): Boolean = delegate == other

            override fun hashCode(): Int = delegate.hashCode()
        }
    }

    private fun untrusted(pkgName: String): AnimeExtension.Untrusted = AnimeExtension.Untrusted(
        name = pkgName,
        pkgName = pkgName,
        versionName = "1.0",
        versionCode = 1,
        libVersion = 1.0,
        signatureHash = "sig-$pkgName",
    )
}
