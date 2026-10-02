package eu.kanade.tachiyomi

import androidx.work.WorkerExceptionInfo
import androidx.work.WorkerParameters
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkManagerConfigurationTest {

    @Test
    fun `worker initialization failure handler reports worker class name and cause`() {
        var recorded: Pair<Exception, String>? = null
        val configuration = buildWorkManagerConfiguration { exception, context ->
            recorded = exception to context
        }

        val handler = configuration.workerInitializationExceptionHandler
        assertNotNull(handler)

        val workerParameters = mockk<WorkerParameters>(relaxed = true)
        val original = IllegalStateException("boom")
        val info = WorkerExceptionInfo("eu.kanade.tachiyomi.TestWorker", workerParameters, original)

        handler.accept(info)

        assertNotNull(recorded)
        val (exception, context) = requireNotNull(recorded)
        assertEquals("worker_initialization", context)
        assertTrue(exception.message!!.contains("eu.kanade.tachiyomi.TestWorker"))
        assertSame(original, exception.cause)
    }
}