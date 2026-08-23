package tachiyomi.core.common.util.system

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NonFatalReporterTest {

    private class ReportedError(message: String) : RuntimeException(message)

    @Test
    fun `fires once per key`() {
        val reported = mutableListOf<Pair<Throwable, String?>>()
        NonFatalReporter.install { throwable, context -> reported.add(throwable to context) }

        val error = ReportedError("first")
        NonFatalReporter.reportOnce("test.once.key", error, "context-a")
        NonFatalReporter.reportOnce("test.once.key", ReportedError("second"), "context-b")

        reported.shouldContainExactly(error to "context-a")
    }

    @Test
    fun `distinct keys each fire`() {
        val reported = mutableListOf<String>()
        NonFatalReporter.install { _, context -> reported.add(context ?: "") }

        NonFatalReporter.reportOnce("test.distinct.one", ReportedError("one"), "one")
        NonFatalReporter.reportOnce("test.distinct.two", ReportedError("two"), "two")

        reported.shouldContainExactly("one", "two")
    }

    @Test
    fun `no handler is safe`() {
        NonFatalReporter.install { _, _ -> }
        // A fresh process never installs a handler. The default must not throw.
        NonFatalReporter.reportOnce("test.nohandler.key", ReportedError("no handler"))
    }

    @Test
    fun `handler exceptions are swallowed`() {
        val reported = mutableListOf<String>()
        NonFatalReporter.install { _, _ -> throw IllegalStateException("broken handler") }

        NonFatalReporter.reportOnce("test.throwing.key", ReportedError("boom"), "context")

        reported shouldBe emptyList()
    }
}
