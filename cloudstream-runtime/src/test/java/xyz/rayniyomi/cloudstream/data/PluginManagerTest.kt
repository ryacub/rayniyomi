package xyz.rayniyomi.cloudstream.data

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class PluginManagerTest {

    private val dir = createTempDirectory("cs-manager").toFile()
    private val pluginDir = File(dir, "plugins")
    private val store = PluginStore(File(dir, "state.json")) {}
    private val repo = "https://example.com/repo.json"
    private val listUrl = "https://example.com/plugins.json"
    private val archive = cs3()
    private val fetcher = FakeFetcher()
    private val manager = PluginManager(store, pluginDir, fetcher)

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun cs3(): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            listOf("manifest.json", "classes.dex").forEach {
                zip.putNextEntry(ZipEntry(it))
                zip.write(it.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun entry(name: String, hash: String? = sha256(archive), version: Int = 1) = PluginEntry(
        url = "https://example.com/$name.cs3",
        version = version,
        name = name,
        internalName = name,
        language = "ar",
        tvTypes = listOf("Anime"),
        fileHash = hash?.let { "sha256-$it" },
    )

    private inner class FakeFetcher : PluginManager.Fetcher {
        val texts = mutableMapOf(
            repo to """{"name":"Example","manifestVersion":1,"pluginLists":["$listUrl"]}""",
            listUrl to
                """[{"url":"https://example.com/A.cs3","version":1,"name":"A","internalName":"A","tvTypes":["Anime"]}]""",
        )
        val requested = mutableListOf<String>()
        var bytesStarted: CompletableDeferred<Unit>? = null
        var delayedBytes: CompletableDeferred<ByteArray>? = null

        override suspend fun text(url: String): String {
            requested += url
            return texts[url] ?: throw IOException("HTTP 404 for $url")
        }

        override suspend fun bytes(url: String): ByteArray {
            requested += url
            bytesStarted?.complete(Unit)
            return delayedBytes?.await() ?: archive
        }
    }

    @Test
    fun `adding a repository stores its manifest name`() = runTest {
        store.update { it.giveConsent() }

        manager.addRepository(repo).isSuccess shouldBe true

        store.state.value.repositories shouldContainExactly listOf(Repository(repo, "Example"))
    }

    @Test
    fun `a repository is not added before consent`() = runTest {
        manager.addRepository(repo).isFailure shouldBe true

        store.state.value.repositories.shouldBeEmpty()
        fetcher.requested.shouldBeEmpty()
    }

    @Test
    fun `an HTTP repository is refused without a request`() = runTest {
        store.update { it.giveConsent() }

        manager.addRepository("http://example.com/repo.json").isFailure shouldBe true

        fetcher.requested.shouldBeEmpty()
    }

    @Test
    fun `a repository that does not load is not added`() = runTest {
        store.update { it.giveConsent() }

        manager.addRepository("https://example.com/missing.json").isFailure shouldBe true

        store.state.value.repositories.shouldBeEmpty()
    }

    @Test
    fun `available plugins come from every plugin list and a failed repository reports its error`() = runTest {
        store.update {
            it.giveConsent().addRepository(repo, "Example").addRepository("https://down.test/repo.json", "Down")
        }

        val available = manager.availablePlugins()

        available.map { it.repository.name } shouldContainExactly listOf("Example", "Down")
        available.first().entries.map { it.internalName } shouldContainExactly listOf("A")
        available.first().error shouldBe null
        available.last().entries.shouldBeEmpty()
        available.last().error shouldNotBe null
    }

    @Test
    fun `installing writes the plugin file and records its hash`() = runTest {
        store.update { it.giveConsent().addRepository(repo, "Example") }

        manager.install(entry("A"), repo).isSuccess shouldBe true

        manager.pluginFile("A").readBytes() shouldBe archive
        store.state.value.installed.single().fileSha256 shouldBe sha256(archive)
    }

    @Test
    fun `a plugin whose hash does not match is not installed`() = runTest {
        store.update { it.giveConsent().addRepository(repo, "Example") }

        manager.install(entry("A", hash = "00".repeat(32)), repo).isFailure shouldBe true

        manager.pluginFile("A").exists() shouldBe false
        store.state.value.installed.shouldBeEmpty()
    }

    @Test
    fun `a plugin without a published hash is not installed`() = runTest {
        store.update { it.giveConsent().addRepository(repo, "Example") }

        manager.install(entry("A", hash = null), repo).isFailure shouldBe true

        store.state.value.installed.shouldBeEmpty()
    }

    @Test
    fun `a plugin name that leaves the plugin folder is refused`() = runTest {
        store.update { it.giveConsent().addRepository(repo, "Example") }

        manager.install(entry("../state"), repo).isFailure shouldBe true

        store.state.value.installed.shouldBeEmpty()
    }

    @Test
    fun `removing a plugin deletes its file`() = runTest {
        store.update { it.giveConsent().addRepository(repo, "Example") }
        manager.install(entry("A"), repo)

        manager.remove("A")

        manager.pluginFile("A").exists() shouldBe false
        store.state.value.installed.shouldBeEmpty()
    }

    @Test
    fun `removing a repository deletes the files of its plugins`() = runTest {
        store.update { it.giveConsent().addRepository(repo, "Example") }
        manager.install(entry("A"), repo)

        manager.removeRepository(repo)

        manager.pluginFile("A").exists() shouldBe false
        store.state.value.repositories.shouldBeEmpty()
    }

    @Test
    fun `a download finishing after its repository is removed and re-added is not installed`() = runTest {
        store.update { it.giveConsent().addRepository(repo, "Example") }
        val bytesStarted = CompletableDeferred<Unit>()
        val delayedBytes = CompletableDeferred<ByteArray>()
        fetcher.bytesStarted = bytesStarted
        fetcher.delayedBytes = delayedBytes

        val pendingInstall = async { manager.install(entry("A"), repo) }
        bytesStarted.await()

        manager.removeRepository(repo)
        manager.addRepository(repo).isSuccess shouldBe true
        delayedBytes.complete(archive)

        pendingInstall.await().isFailure shouldBe true
        manager.pluginFile("A").exists() shouldBe false
        store.state.value.installed.shouldBeEmpty()
    }

    @Test
    fun `a download finishing after plugin removal is not installed`() = runTest {
        store.update { it.giveConsent().addRepository(repo, "Example") }
        val bytesStarted = CompletableDeferred<Unit>()
        val delayedBytes = CompletableDeferred<ByteArray>()
        fetcher.bytesStarted = bytesStarted
        fetcher.delayedBytes = delayedBytes

        val pendingInstall = async { manager.install(entry("A"), repo) }
        bytesStarted.await()

        manager.remove("A")
        delayedBytes.complete(archive)

        pendingInstall.await().isFailure shouldBe true
        manager.pluginFile("A").exists() shouldBe false
        store.state.value.installed.shouldBeEmpty()
    }
}
