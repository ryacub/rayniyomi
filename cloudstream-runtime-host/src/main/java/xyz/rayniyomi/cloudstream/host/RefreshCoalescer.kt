package xyz.rayniyomi.cloudstream.host

class RefreshCoalescer(
    private val delayMs: Long,
    private val scheduler: Scheduler,
    refresh: () -> Unit,
) {
    interface Scheduler {
        fun schedule(delayMs: Long, task: Runnable)
        fun cancel(task: Runnable)
    }

    private val task = Runnable(refresh)

    @Synchronized
    fun trigger() {
        scheduler.cancel(task)
        scheduler.schedule(delayMs, task)
    }
}
