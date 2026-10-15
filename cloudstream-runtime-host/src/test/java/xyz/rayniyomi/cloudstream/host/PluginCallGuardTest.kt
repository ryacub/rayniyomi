package xyz.rayniyomi.cloudstream.host

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class PluginCallGuardTest {

    @Test
    fun `a result passes through`() = runTest {
        guardPluginCall("AnimeWitcher") { 42 } shouldBe 42
    }

    @Test
    fun `a missing plugin method becomes an exception that names the source`() = runTest {
        val error =
            shouldThrow<PluginCallException> { guardPluginCall<Unit>("AnimeWitcher") { throw NotImplementedError() } }

        error.message shouldContain "AnimeWitcher"
        (error.cause is NotImplementedError) shouldBe true
    }

    @Test
    fun `a linkage error becomes an exception`() = runTest {
        shouldThrow<PluginCallException> { guardPluginCall<Unit>("A") { throw NoSuchMethodError("search") } }
    }

    @Test
    fun `an ordinary exception keeps its message`() = runTest {
        val error = shouldThrow<PluginCallException> { guardPluginCall<Unit>("A") { throw IOException("HTTP 403") } }

        error.message shouldContain "HTTP 403"
    }

    @Test
    fun `cancellation is not wrapped`() = runTest {
        shouldThrow<CancellationException> { guardPluginCall<Unit>("A") { throw CancellationException("stop") } }
    }

    @Test
    fun `link loading keeps links emitted before an error`() = runTest {
        val error = guardLinkLoading("A", hasLinks = { true }) { throw IOException("HTTP 500") }

        error?.message shouldContain "HTTP 500"
    }

    @Test
    fun `link loading without links still throws`() = runTest {
        shouldThrow<PluginCallException> {
            guardLinkLoading("A", hasLinks = { false }) { throw IOException("HTTP 500") }
        }
    }

    @Test
    fun `link loading that succeeds returns no error`() = runTest {
        guardLinkLoading("A", hasLinks = { true }) {} shouldBe null
    }

    @Test
    fun `link loading propagates cancellation even with links`() = runTest {
        shouldThrow<CancellationException> {
            guardLinkLoading("A", hasLinks = { true }) { throw CancellationException("stop") }
        }
    }
}
