package eu.kanade.tachiyomi.test

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 extension that installs the [VirtualTime] dispatchers before each test and
 * removes them after it. Register it with [org.junit.jupiter.api.extension.RegisterExtension]
 * when the test needs the scheduler:
 *
 * ```
 * @RegisterExtension
 * val vt = VirtualTimeExtension()
 *
 * @Test
 * fun `example`() = runTest(vt.scheduler) { ... }
 * ```
 *
 * Use `@ExtendWith(VirtualTimeExtension::class)` instead when the test only needs the
 * dispatchers installed and drives no scheduling itself.
 */
class VirtualTimeExtension :
    BeforeEachCallback,
    AfterEachCallback {

    val virtualTime = VirtualTime()

    /** Shared scheduler, for `runTest(vt.scheduler)`. */
    val scheduler get() = virtualTime.scheduler

    override fun beforeEach(context: ExtensionContext) {
        virtualTime.setUpMain()
    }

    override fun afterEach(context: ExtensionContext) {
        virtualTime.tearDownMain()
    }
}
