package eu.kanade.tachiyomi.extension.anime.api

import eu.kanade.tachiyomi.di.testAppGraph
import eu.kanade.tachiyomi.network.NetworkHelper
import io.kotest.matchers.collections.shouldBeEmpty
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import logcat.LogcatLogger
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import okhttp3.Call
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Collections

class AnimeExtensionApiTest {

    private val errors = Collections.synchronizedList(mutableListOf<String>())

    @BeforeEach
    fun setUp() {
        LogcatLogger.uninstall()
        LogcatLogger.install(
            object : LogcatLogger {
                override fun isLoggable(priority: LogPriority) = true

                override fun log(priority: LogPriority, tag: String, message: String) {
                    if (priority == LogPriority.ERROR) errors += message
                }
            },
        )
    }

    @AfterEach
    fun tearDown() {
        LogcatLogger.uninstall()
        every { testAppGraph.networkHelper } returns mockk(relaxed = true)
        every { testAppGraph.getAnimeExtensionRepo } returns mockk(relaxed = true)
    }

    @Test
    fun `cancelled refresh does not log the repo as failed`() = runBlocking<Unit> {
        val requested = CompletableDeferred<Unit>()
        val pendingCall = mockk<Call> {
            every { enqueue(any()) } answers { requested.complete(Unit) }
            every { cancel() } just runs
        }
        every { testAppGraph.networkHelper } returns mockk<NetworkHelper> {
            every { client } returns mockk<OkHttpClient> { every { newCall(any()) } returns pendingCall }
        }
        every { testAppGraph.getAnimeExtensionRepo } returns mockk<GetAnimeExtensionRepo> {
            coEvery { getAll() } returns listOf(
                ExtensionRepo(
                    baseUrl = "https://repo.test",
                    name = "Repo",
                    shortName = null,
                    website = "",
                    signingKeyFingerprint = "hash",
                ),
            )
        }

        val refresh = async { AnimeExtensionApi().findExtensions() }
        requested.await()
        refresh.cancelAndJoin()

        errors.shouldBeEmpty()
    }
}
