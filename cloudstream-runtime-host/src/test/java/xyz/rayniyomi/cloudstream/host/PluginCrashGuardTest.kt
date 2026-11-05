package xyz.rayniyomi.cloudstream.host

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PluginCrashGuardTest {

    @TempDir
    lateinit var directory: File

    private val moosa = PluginKey("MOOSAHub", "4a5df8bac1ada4e5d0", hostVersion = "579-1")
    private val streamPlay = PluginKey("StreamPlay", "aa11bb22cc33dd44ee", hostVersion = "579-1")
    private val animePahe = PluginKey("AnimePahe", "ff11ee22dd33cc44bb", hostVersion = "579-1")
    private val originalDefault = Thread.getDefaultUncaughtExceptionHandler()

    @AfterEach
    fun restoreDefaultHandler() {
        Thread.setDefaultUncaughtExceptionHandler(originalDefault)
        System.getProperties().keys.removeIf { it.toString().startsWith("xyz.rayniyomi.cloudstream.crashGuard") }
    }

    private fun index() = PluginCrashIndex().apply {
        add(LoadedPlugin(moosa, "/data/no_backup/cloudstream/plugins/MOOSAHub-4a5df8bac1ada4e5.cs3", "com.moosa.hub") { true })
    }

    private fun throwableAt(vararg classes: String, message: String? = null) = NoClassDefFoundError(message).apply {
        stackTrace = classes.map { StackTraceElement(it, "run", "File.kt", 1) }.toTypedArray()
    }

    @Test
    fun `a frame in a plugin package that the plugin loader owns names that plugin`() {
        val error = throwableAt("dalvik.system.DexFile", "com.moosa.hub.MOOSAHubPlugin", "xyz.rayniyomi.cloudstream.host.PluginLoader")

        index().find(error) shouldBe moosa
    }

    @Test
    fun `a cs3 path in a cause message names that plugin`() {
        val cause = ClassNotFoundException(
            "Didn't find class on path: DexPathList[[zip file \"/data/no_backup/cloudstream/plugins/MOOSAHub-4a5df8bac1ada4e5.cs3\"]]",
        )
        val error = throwableAt("android.os.Handler").apply { initCause(cause) }

        index().find(error) shouldBe moosa
    }

    @Test
    fun `a suppressed throwable is searched`() {
        val error = throwableAt("android.os.Handler").apply { addSuppressed(throwableAt("com.moosa.hub.MOOSASeeAll")) }

        index().find(error) shouldBe moosa
    }

    @Test
    fun `a host-only stack names no plugin`() {
        val error = throwableAt("eu.kanade.tachiyomi.ui.main.MainActivity", "org.jsoup.nodes.Element")

        index().find(error) shouldBe null
    }

    @Test
    fun `a shared package names the one plugin whose loader owns the class`() {
        val index = PluginCrashIndex().apply {
            add(LoadedPlugin(streamPlay, "/p/StreamPlay.cs3", "com.phisher98") { it == "com.phisher98.StreamPlayProvider" })
            add(LoadedPlugin(animePahe, "/p/AnimePahe.cs3", "com.phisher98") { it == "com.phisher98.AnimePahe" })
        }

        index.find(throwableAt("com.phisher98.AnimePahe")) shouldBe animePahe
    }

    @Test
    fun `a class that two plugin loaders own names no plugin`() {
        val index = PluginCrashIndex().apply {
            add(LoadedPlugin(streamPlay, "/p/StreamPlay.cs3", "com.phisher98") { true })
            add(LoadedPlugin(animePahe, "/p/AnimePahe.cs3", "com.phisher98") { true })
        }

        index.find(throwableAt("com.phisher98.CloudflareWebViewDialog")) shouldBe null
    }

    @Test
    fun `a cause cycle ends the search`() {
        val first = RuntimeException("first")
        val second = RuntimeException("second", first)
        first.initCause(second)

        index().find(first) shouldBe null
    }

    @Test
    fun `the guard quarantines the plugin and forwards the crash`() {
        val forwarded = mutableListOf<Throwable>()
        val quarantine = PluginQuarantine(directory)
        val error = throwableAt("com.moosa.hub.MOOSASeeAll")

        PluginCrashGuard(index(), quarantine) { _, e -> forwarded += e }.uncaughtException(Thread.currentThread(), error)

        quarantine.reason(moosa) shouldBe error.toString()
        forwarded.single() shouldBeSameInstanceAs error
    }

    @Test
    fun `the guard forwards the crash when the quarantine write fails`() {
        val forwarded = mutableListOf<Throwable>()
        val blocked = File(directory, "file").apply { writeText("x") }
        val error = throwableAt("com.moosa.hub.MOOSASeeAll")

        PluginCrashGuard(index(), PluginQuarantine(File(blocked, "quarantine"))) { _, e -> forwarded += e }
            .uncaughtException(Thread.currentThread(), error)

        forwarded.single() shouldBeSameInstanceAs error
    }

    @Test
    fun `a second install replaces the guard and keeps the original handler as next`() {
        val forwarded = mutableListOf<Throwable>()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> forwarded += e }
        val quarantine = PluginQuarantine(directory)

        installCrashGuard(PluginCrashIndex(), quarantine)
        installCrashGuard(index(), quarantine)
        val error = throwableAt("com.moosa.hub.MOOSASeeAll")
        Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)

        forwarded shouldContainExactly listOf(error)
        quarantine.reason(moosa) shouldBe error.toString()
    }
}
