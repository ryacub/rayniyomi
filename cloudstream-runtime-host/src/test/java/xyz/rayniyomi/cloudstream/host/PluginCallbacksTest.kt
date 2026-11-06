package xyz.rayniyomi.cloudstream.host

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.mockk
import org.junit.jupiter.api.Test

class PluginCallbacksTest {

    private val registered = mutableListOf<ActivityLifecycleCallbacks>()
    private val failures = mutableListOf<Throwable>()
    private val callbacks = PluginCallbacks(
        register = { registered += it },
        unregister = { registered -= it },
        onFailure = { failures += it },
    )
    private val activity = mockk<Activity>()

    @Test
    fun `a callback that throws detaches every callback of the plugin and reports the error`() {
        val stored = NoClassDefFoundError("Failed resolution of: Landroidx/fragment/app/FragmentManager;")
        callbacks.add(Recording(onResumed = { throw stored }))
        callbacks.add(Recording())

        registered.first().onActivityResumed(activity)

        registered.shouldBeEmpty()
        failures shouldHaveSize 1
        failures.single() shouldBeSameInstanceAs stored
    }

    @Test
    fun `a healthy callback receives the event`() {
        val healthy = Recording()
        callbacks.add(healthy)

        registered.single().onActivityResumed(activity)

        healthy.resumed shouldContainExactly listOf(activity)
        failures.shouldBeEmpty()
    }

    @Test
    fun `remove unregisters the guard of that callback only`() {
        val first = Recording()
        val second = Recording()
        callbacks.add(first)
        callbacks.add(second)
        val secondGuard = registered[1]

        callbacks.remove(first)

        registered shouldContainExactly listOf(secondGuard)
    }

    @Test
    fun `detachAll unregisters every guard`() {
        callbacks.add(Recording())
        callbacks.add(Recording())

        callbacks.detachAll()

        registered.shouldBeEmpty()
        failures.size shouldBe 0
    }

    @Test
    fun `a failure handler that throws does not escape to the activity dispatch`() {
        val throwingHandler = PluginCallbacks(
            register = { registered += it },
            unregister = { registered -= it },
            onFailure = { error("quarantine write failed") },
        )
        throwingHandler.add(Recording(onResumed = { throw NoClassDefFoundError() }))

        registered.single().onActivityResumed(activity)

        registered.shouldBeEmpty()
    }

    @Test
    fun `a guard already queued in the failing dispatch does not run its callback`() {
        val later = Recording()
        callbacks.add(Recording(onResumed = { throw NoClassDefFoundError() }))
        callbacks.add(later)
        val dispatch = registered.toList()

        dispatch.forEach { it.onActivityResumed(activity) }

        later.resumed.shouldBeEmpty()
        failures shouldHaveSize 1
    }

    @Test
    fun `a plugin cannot register callbacks again after it was detached`() {
        callbacks.detachAll()

        callbacks.add(Recording())

        registered.shouldBeEmpty()
    }

    @Test
    fun `adding the same callback twice leaves no guard after detachAll`() {
        val callback = Recording()
        callbacks.add(callback)
        callbacks.add(callback)

        callbacks.detachAll()

        registered.shouldBeEmpty()
    }

    private class Recording(private val onResumed: () -> Unit = {}) : ActivityLifecycleCallbacks {
        val resumed = mutableListOf<Activity>()

        override fun onActivityResumed(activity: Activity) {
            onResumed()
            resumed += activity
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
