package xyz.rayniyomi.cloudstream.host

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RefreshCoalescerTest {

    private class FakeScheduler : RefreshCoalescer.Scheduler {
        val pending = mutableListOf<Runnable>()
        val delays = mutableListOf<Long>()

        override fun schedule(delayMs: Long, task: Runnable) {
            delays += delayMs
            pending += task
        }

        override fun cancel(task: Runnable) {
            pending -= task
        }

        fun runPending() {
            val tasks = pending.toList()
            pending.clear()
            tasks.forEach(Runnable::run)
        }
    }

    @Test
    fun `several changes inside the delay give one refresh`() {
        val scheduler = FakeScheduler()
        var refreshes = 0
        val coalescer = RefreshCoalescer(500, scheduler) { refreshes++ }

        repeat(3) { coalescer.trigger() }
        scheduler.runPending()

        refreshes shouldBe 1
        scheduler.delays.toSet() shouldBe setOf(500L)
    }

    @Test
    fun `a change after a refresh gives another refresh`() {
        val scheduler = FakeScheduler()
        var refreshes = 0
        val coalescer = RefreshCoalescer(500, scheduler) { refreshes++ }

        coalescer.trigger()
        scheduler.runPending()
        coalescer.trigger()
        scheduler.runPending()

        refreshes shouldBe 2
    }
}
