package eu.kanade.tachiyomi.network.interceptor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class HostChallengeTest {

    private val host = HostChallenge()

    @Test
    fun `a request that waited while another solved does not solve again`() {
        val attemptsBefore = host.attemptCount()
        val firstSolving = CountDownLatch(1)
        val finishFirst = CountDownLatch(1)
        val first = thread {
            host.solveUnlessAttemptedSince(attemptsBefore) {
                firstSolving.countDown()
                finishFirst.await()
                true
            }
        }
        assertTrue(firstSolving.await(5, TimeUnit.SECONDS))

        var secondSolved = false
        var secondResult = false
        val second = thread {
            secondResult = host.solveUnlessAttemptedSince(attemptsBefore) {
                secondSolved = true
                true
            }
        }
        Thread.sleep(200)
        finishFirst.countDown()
        first.join(5_000)
        second.join(5_000)

        assertFalse(secondSolved)
        assertTrue(secondResult)
    }

    @Test
    fun `a request that waited on a failed solve does not solve again`() {
        val attemptsBefore = host.attemptCount()
        assertThrows(IllegalStateException::class.java) {
            host.solveUnlessAttemptedSince(attemptsBefore) { error("challenge failed") }
        }

        var solved = false
        val result = host.solveUnlessAttemptedSince(attemptsBefore) {
            solved = true
            true
        }

        assertFalse(solved)
        assertTrue(result)
    }

    @Test
    fun `a request that starts after a failed solve solves again`() {
        assertThrows(IllegalStateException::class.java) {
            host.solveUnlessAttemptedSince(host.attemptCount()) { error("challenge failed") }
        }

        var solved = false
        host.solveUnlessAttemptedSince(host.attemptCount()) {
            solved = true
            true
        }

        assertTrue(solved)
    }

    @Test
    fun `a solve that could not run is not counted`() {
        val attemptsBefore = host.attemptCount()

        val result = host.solveUnlessAttemptedSince(attemptsBefore) { false }

        assertFalse(result)
        assertEquals(attemptsBefore, host.attemptCount())
    }
}
