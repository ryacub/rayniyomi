package tachiyomi.core.common.util.lang

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import rx.Observable
import rx.plugins.RxJavaHooks
import rx.schedulers.Schedulers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * A defective extension raises [LinkageError] from its own code. RxJava 1 treats it as fatal and
 * rethrows it instead of giving it to `onError`, so the process stops. These tests hold the guard
 * that changes it into an ordinary exception.
 */
class SourceLinkageGuardTest {

    @BeforeEach
    fun setUp() {
        RxJavaHooks.reset()
        SourceLinkageGuard.install()
    }

    @AfterEach
    fun tearDown() {
        RxJavaHooks.reset()
        SourceLinkageReporter.onFailure = {}
    }

    private fun brokenObservable(): Observable<String> = Observable.unsafeCreate {
        throw NoSuchMethodError("runBlockingK\$default")
    }

    @Test
    fun `synchronous LinkageError reaches onError as SourceLinkageException`() {
        val caught = AtomicReference<Throwable>()

        brokenObservable().subscribe({ }, { caught.set(it) })

        caught.get().shouldBeInstanceOf<SourceLinkageException>()
        caught.get().cause.shouldBeInstanceOf<NoSuchMethodError>()
    }

    @Test
    fun `LinkageError on a scheduler thread reaches onError instead of killing the thread`() {
        val caught = AtomicReference<Throwable>()
        val latch = CountDownLatch(1)

        brokenObservable()
            .subscribeOn(Schedulers.io())
            .subscribe({ }, {
                caught.set(it)
                latch.countDown()
            })

        latch.await(5, TimeUnit.SECONDS) shouldBe true
        caught.get().shouldBeInstanceOf<SourceLinkageException>()
    }

    @Test
    fun `awaitSingle reports a LinkageError as an Exception`() {
        assertThrows<SourceLinkageException> {
            runBlocking { brokenObservable().awaitSingle() }
        }
    }

    @Test
    fun `a contained error is reported so the extension can be identified`() {
        val reported = mutableListOf<SourceLinkageException>()
        SourceLinkageReporter.onFailure = { reported += it }

        brokenObservable().subscribe({ }, { })

        reported.size shouldBe 1
        reported.first().cause.shouldBeInstanceOf<NoSuchMethodError>()
    }

    @Test
    fun `a reporter fault does not replace the original fault`() {
        SourceLinkageReporter.onFailure = { throw IllegalStateException("reporter broken") }
        val caught = AtomicReference<Throwable>()

        brokenObservable().subscribe({ }, { caught.set(it) })

        caught.get().shouldBeInstanceOf<SourceLinkageException>()
    }

    @Test
    fun `a working observable is unchanged`() {
        runBlocking { Observable.just("ok").awaitSingle() } shouldBe "ok"
    }

    @Test
    fun `an ordinary exception keeps the normal error path`() {
        val caught = AtomicReference<Throwable>()

        Observable.unsafeCreate<String> { throw IllegalStateException("boom") }
            .subscribe({ }, { caught.set(it) })

        caught.get().shouldBeInstanceOf<IllegalStateException>()
    }

    @Test
    fun `a VirtualMachineError is not contained`() {
        assertThrows<StackOverflowError> {
            Observable.unsafeCreate<String> { throw StackOverflowError() }
                .subscribe({ }, { })
        }
    }
}
