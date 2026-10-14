package eu.kanade.tachiyomi.network.interceptor

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
        val solvesBefore = host.solveCount()
        val firstSolving = CountDownLatch(1)
        val finishFirst = CountDownLatch(1)
        val first = thread {
            host.solveUnlessSolvedSince(solvesBefore) {
                firstSolving.countDown()
                finishFirst.await()
                true
            }
        }
        assertTrue(firstSolving.await(5, TimeUnit.SECONDS))

        var secondSolved = false
        var secondResult = false
        val second = thread {
            secondResult = host.solveUnlessSolvedSince(solvesBefore) {
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
    fun `a failed solve lets the next request solve`() {
        val solvesBefore = host.solveCount()
        assertThrows(IllegalStateException::class.java) {
            host.solveUnlessSolvedSince(solvesBefore) { error("challenge failed") }
        }

        var solved = false
        host.solveUnlessSolvedSince(solvesBefore) {
            solved = true
            true
        }

        assertTrue(solved)
    }

    @Test
    fun `a solve that could not run is not counted`() {
        val solvesBefore = host.solveCount()

        val result = host.solveUnlessSolvedSince(solvesBefore) { false }

        assertFalse(result)
        assertTrue(host.solveCount() == solvesBefore)
    }
}
