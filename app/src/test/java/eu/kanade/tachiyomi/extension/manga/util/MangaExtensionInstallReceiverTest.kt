package eu.kanade.tachiyomi.extension.manga.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import eu.kanade.tachiyomi.extension.manga.model.MangaExtension
import eu.kanade.tachiyomi.extension.manga.model.MangaLoadResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class MangaExtensionInstallReceiverTest {

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun replacedIntent(pkgName: String): Intent =
        mockk {
            every { action } returns Intent.ACTION_PACKAGE_REPLACED
            every { getBooleanExtra(Intent.EXTRA_REPLACING, false) } returns true
            every { data } returns mockk<Uri> { every { encodedSchemeSpecificPart } returns pkgName }
        }

    private fun addedIntent(pkgName: String): Intent =
        mockk {
            every { action } returns Intent.ACTION_PACKAGE_ADDED
            every { getBooleanExtra(Intent.EXTRA_REPLACING, false) } returns false
            every { data } returns mockk<Uri> { every { encodedSchemeSpecificPart } returns pkgName }
        }

    @Test
    fun `unregister cancels pending replace retry work`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val listener = mockk<MangaExtensionInstallReceiver.Listener>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val receiver = MangaExtensionInstallReceiver(listener, scope)

        mockkObject(MangaExtensionLoader)
        coEvery {
            MangaExtensionLoader.loadMangaExtensionFromPkgName(any(), "pkg1")
        } returnsMany listOf(
            MangaLoadResult.Error,
            MangaLoadResult.Error,
            MangaLoadResult.Success(mockk(relaxed = true)),
        )

        receiver.onReceive(context, replacedIntent("pkg1"))

        // Enter the first delay(500) of the replace retry loop.
        advanceTimeBy(600)
        coVerify(exactly = 2) {
            MangaExtensionLoader.loadMangaExtensionFromPkgName(any(), "pkg1")
        }

        receiver.unregister(context)
        advanceUntilIdle()

        // The third attempt never runs and the listener is never notified.
        coVerify(exactly = 2) {
            MangaExtensionLoader.loadMangaExtensionFromPkgName(any(), "pkg1")
        }
        verify(exactly = 0) { listener.onExtensionUpdated(any()) }
    }

    @Test
    fun `unregister unregisters the receiver and tolerates repeat calls`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val listener = mockk<MangaExtensionInstallReceiver.Listener>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val receiver = MangaExtensionInstallReceiver(listener, scope)

        // Model real Android behavior: a second unregistration throws.
        var unregistered = false
        every { context.unregisterReceiver(receiver) } answers {
            if (unregistered) {
                throw IllegalArgumentException("Receiver not registered")
            }
            unregistered = true
        }

        receiver.unregister(context)
        receiver.unregister(context)

        verify(exactly = 2) { context.unregisterReceiver(receiver) }
    }

    @Test
    fun `added action still notifies listener before unregister`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val listener = mockk<MangaExtensionInstallReceiver.Listener>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val receiver = MangaExtensionInstallReceiver(listener, scope)

        mockkObject(MangaExtensionLoader)
        coEvery {
            MangaExtensionLoader.loadMangaExtensionFromPkgName(any(), "pkg2")
        } returns MangaLoadResult.Success(mockk(relaxed = true))

        receiver.onReceive(context, addedIntent("pkg2"))
        advanceUntilIdle()

        verify(exactly = 1) { listener.onExtensionInstalled(any()) }
    }
}
