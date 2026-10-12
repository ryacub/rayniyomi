package xyz.rayniyomi.plugin.lightnovel

import android.content.Intent
import android.os.StrictMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class ReaderActivityThreadingTest {
    @Test
    fun openingMissingBookDoesNotReadLibraryOnMainThread() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val violations = CopyOnWriteArrayList<Any>()
        lateinit var oldPolicy: StrictMode.ThreadPolicy
        try {
            instrumentation.runOnMainSync {
                oldPolicy = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(
                    StrictMode.ThreadPolicy.Builder(oldPolicy)
                        .detectDiskReads()
                        .penaltyListener({ it.run() }, { violations.add(it) })
                        .build(),
                )
            }
            instrumentation.startActivitySync(
                Intent(instrumentation.targetContext, ReaderActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(
                        ReaderActivity.EXTRA_BOOK_ID,
                        UUID.randomUUID().toString(),
                    ),
            )

            assertTrue("ReaderActivity performed disk reads on the main thread: $violations", violations.isEmpty())
        } finally {
            instrumentation.runOnMainSync { StrictMode.setThreadPolicy(oldPolicy) }
        }
    }
}
