package eu.kanade.tachiyomi

import eu.kanade.tachiyomi.data.translation.TranslationManager
import eu.kanade.tachiyomi.data.translation.TranslationNotifier
import eu.kanade.tachiyomi.di.AppGraph
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class AppGraphWarmupTest {
    @Test
    fun `startup graph resolves off caller thread while notifications stay on caller scope`() = runBlocking {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { callerDispatcher ->
            val callerThread = withContext(callerDispatcher) { Thread.currentThread() }
            val scope = CoroutineScope(Job() + callerDispatcher)
            try {
                val threads = ConcurrentHashMap<String, Thread>()
                fun record(name: String) {
                    threads[name] = Thread.currentThread()
                }
                val graph = mockk<AppGraph>(relaxed = true)
                every { graph.networkHelper } answers {
                    record("network")
                    mockk(relaxed = true)
                }
                every { graph.mangaSourceManager } answers {
                    record("mangaSource")
                    mockk(relaxed = true)
                }
                every { graph.animeSourceManager } answers {
                    record("animeSource")
                    mockk(relaxed = true)
                }
                every { graph.database } answers {
                    record("mangaDatabase")
                    mockk(relaxed = true)
                }
                every { graph.animeDatabase } answers {
                    record("animeDatabase")
                    mockk(relaxed = true)
                }
                every { graph.mangaDownloadManager } answers {
                    record("mangaDownload")
                    mockk(relaxed = true)
                }
                every { graph.animeDownloadManager } answers {
                    record("animeDownload")
                    mockk(relaxed = true)
                }
                every { graph.lightNovelPluginManager } answers {
                    record("novelPlugin")
                    mockk(relaxed = true)
                }
                val manager = mockk<TranslationManager> {
                    every { translationStates } returns MutableStateFlow(emptyMap())
                    every { chapterTitles } returns MutableStateFlow(emptyMap())
                }
                every { graph.translationManager } answers {
                    record("translation")
                    manager
                }
                val notificationThread = CompletableDeferred<Thread>()
                val notifier = mockk<TranslationNotifier> {
                    every { onStatesChanged(any(), any()) } answers {
                        notificationThread.complete(Thread.currentThread())
                        Unit
                    }
                }

                withTimeout(10_000) {
                    graph.warmUp(scope, notifier).join()
                    assertSame(callerThread, notificationThread.await())
                }
                assertEquals(
                    setOf(
                        "network", "mangaSource", "animeSource", "mangaDatabase", "animeDatabase",
                        "mangaDownload", "animeDownload", "novelPlugin", "translation",
                    ),
                    threads.keys,
                )
                assertTrue(
                    threads.values.all {
                        it !== callerThread
                    },
                    "Startup graph must resolve off the caller thread",
                )
            } finally {
                scope.cancel()
            }
        }
    }
}
