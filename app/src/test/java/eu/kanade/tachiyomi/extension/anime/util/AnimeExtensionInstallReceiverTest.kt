package eu.kanade.tachiyomi.extension.anime.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.extension.anime.model.AnimeLoadResult
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnimeExtensionInstallReceiverTest {

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

    private fun removedIntent(pkgName: String, replacing: Boolean = false): Intent =
        mockk {
            every { action } returns Intent.ACTION_PACKAGE_REMOVED
            every { getBooleanExtra(Intent.EXTRA_REPLACING, false) } returns replacing
            every { data } returns mockk<Uri> { every { encodedSchemeSpecificPart } returns pkgName }
        }

    @Test
    fun `unregister cancels pending replace retry work`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val listener = mockk<AnimeExtensionInstallReceiver.Listener>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val receiver = AnimeExtensionInstallReceiver(listener, scope)

        mockkObject(AnimeExtensionLoader)
        coEvery {
            AnimeExtensionLoader.loadExtensionFromPkgName(any(), "pkg1")
        } returnsMany listOf(
            AnimeLoadResult.Error,
            AnimeLoadResult.Error,
            AnimeLoadResult.Success(mockk(relaxed = true)),
        )

        receiver.onReceive(context, replacedIntent("pkg1"))

        // Enter the first delay(500) of the replace retry loop.
        advanceTimeBy(600)
        coVerify(exactly = 2) {
            AnimeExtensionLoader.loadExtensionFromPkgName(any(), "pkg1")
        }

        receiver.unregister(context)
        advanceUntilIdle()

        // The third attempt never runs and the listener is never notified.
        coVerify(exactly = 2) {
            AnimeExtensionLoader.loadExtensionFromPkgName(any(), "pkg1")
        }
        verify(exactly = 0) { listener.onExtensionUpdated(any()) }
    }

    @Test
    fun `unregister unregisters the receiver and tolerates repeat calls`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val listener = mockk<AnimeExtensionInstallReceiver.Listener>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val receiver = AnimeExtensionInstallReceiver(listener, scope)

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
        val listener = mockk<AnimeExtensionInstallReceiver.Listener>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val receiver = AnimeExtensionInstallReceiver(listener, scope)

        mockkObject(AnimeExtensionLoader)
        coEvery {
            AnimeExtensionLoader.loadExtensionFromPkgName(any(), "pkg2")
        } returns AnimeLoadResult.Success(mockk(relaxed = true))

        receiver.onReceive(context, addedIntent("pkg2"))
        advanceUntilIdle()

        verify(exactly = 1) { listener.onExtensionInstalled(any()) }
    }

    @Test
    fun `custom extension added action routes like system added action`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val listener = mockk<AnimeExtensionInstallReceiver.Listener>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val receiver = AnimeExtensionInstallReceiver(listener, scope)

        mockkObject(AnimeExtensionLoader)
        coEvery {
            AnimeExtensionLoader.loadExtensionFromPkgName(any(), "pkg3")
        } returns AnimeLoadResult.Success(mockk(relaxed = true))

        val intent = mockk<Intent> {
            every { action } returns "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_ADDED"
            every { getBooleanExtra(Intent.EXTRA_REPLACING, false) } returns false
            every { data } returns mockk<Uri> { every { encodedSchemeSpecificPart } returns "pkg3" }
        }

        receiver.onReceive(context, intent)
        advanceUntilIdle()

        verify(exactly = 1) { listener.onExtensionInstalled(any()) }
    }

    @Test
    fun `replace retry succeeds after transient errors`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val listener = mockk<AnimeExtensionInstallReceiver.Listener>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val receiver = AnimeExtensionInstallReceiver(listener, scope)

        mockkObject(AnimeExtensionLoader)
        coEvery {
            AnimeExtensionLoader.loadExtensionFromPkgName(any(), "pkg1")
        } returnsMany listOf(
            AnimeLoadResult.Error,
            AnimeLoadResult.Error,
            AnimeLoadResult.Success(mockk(relaxed = true)),
        )

        receiver.onReceive(context, replacedIntent("pkg1"))
        advanceUntilIdle()

        coVerify(exactly = 3) {
            AnimeExtensionLoader.loadExtensionFromPkgName(any(), "pkg1")
        }
        verify(exactly = 1) { listener.onExtensionUpdated(any()) }
    }

    @Test
    fun `removed action notifies uninstall listener only when not replacing`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val listener = mockk<AnimeExtensionInstallReceiver.Listener>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val receiver = AnimeExtensionInstallReceiver(listener, scope)

        receiver.onReceive(context, removedIntent("pkg1"))
        verify(exactly = 1) { listener.onPackageUninstalled("pkg1") }

        receiver.onReceive(context, removedIntent("pkg2", replacing = true))
        verify(exactly = 0) { listener.onPackageUninstalled("pkg2") }
    }

    @Test
    fun `untrusted result routes to untrusted callback`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val listener = mockk<AnimeExtensionInstallReceiver.Listener>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val receiver = AnimeExtensionInstallReceiver(listener, scope)

        mockkObject(AnimeExtensionLoader)
        coEvery {
            AnimeExtensionLoader.loadExtensionFromPkgName(any(), "pkg1")
        } returns AnimeLoadResult.Untrusted(mockk(relaxed = true))

        receiver.onReceive(context, addedIntent("pkg1"))
        advanceUntilIdle()

        verify(exactly = 1) { listener.onExtensionUntrusted(any()) }
        verify(exactly = 0) { listener.onExtensionInstalled(any()) }
    }

    @Test
    fun `custom extension replaced action notifies update listener`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val listener = mockk<AnimeExtensionInstallReceiver.Listener>(relaxed = true)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val receiver = AnimeExtensionInstallReceiver(listener, scope)

        mockkObject(AnimeExtensionLoader)
        coEvery {
            AnimeExtensionLoader.loadExtensionFromPkgName(any(), "pkg1")
        } returns AnimeLoadResult.Success(mockk(relaxed = true))

        val intent = mockk<Intent> {
            every { action } returns "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_REPLACED"
            every { getBooleanExtra(Intent.EXTRA_REPLACING, false) } returns true
            every { data } returns mockk<Uri> { every { encodedSchemeSpecificPart } returns "pkg1" }
        }

        receiver.onReceive(context, intent)
        advanceUntilIdle()

        verify(exactly = 1) { listener.onExtensionUpdated(any()) }
    }

    @Test
    fun `register filter contains all six actions and package scheme`() {
        val context = mockk<Context>(relaxed = true)
        val listener = mockk<AnimeExtensionInstallReceiver.Listener>(relaxed = true)

        val capturedActions = mutableListOf<String>()
        val capturedSchemes = mutableListOf<String>()
        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(capture(capturedActions)) } just Runs
        every { anyConstructed<IntentFilter>().addDataScheme(capture(capturedSchemes)) } just Runs

        val receiver = AnimeExtensionInstallReceiver(listener)

        receiver.register(context)

        assertEquals(
            listOf(
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_PACKAGE_REMOVED,
                "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_ADDED",
                "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_REPLACED",
                "${BuildConfig.APPLICATION_ID}.ACTION_EXTENSION_REMOVED",
            ),
            capturedActions,
        )
        assertEquals(listOf("package"), capturedSchemes)
    }
}
