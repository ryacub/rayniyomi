package xyz.rayniyomi.cloudstream.ui

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import xyz.rayniyomi.cloudstream.data.PluginManager
import xyz.rayniyomi.cloudstream.data.PluginStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

@OptIn(ExperimentalCoroutinesApi::class)
class PluginManagerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val dir = createTempDirectory("cs-viewmodel").toFile()
    private val store = PluginStore(File(dir, "state.json")) {}
    private val repo = "https://example.com/repo.json"
    private val listUrl = "https://example.com/plugins.json"
    private val archive = cs3()
    private val texts = mutableMapOf(
        repo to """{"name":"Example","manifestVersion":1,"pluginLists":["$listUrl"]}""",
        listUrl to pluginList(version = 1),
    )
    private val fetcher = object : PluginManager.Fetcher {
        override suspend fun text(url: String): String = texts[url] ?: throw IOException("HTTP 404 for $url")
        override suspend fun bytes(url: String): ByteArray = archive
    }
    private val manager = PluginManager(store, File(dir, "plugins"), fetcher)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    private fun viewModel() = PluginManagerViewModel(manager, store, dispatcher)

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

    private fun pluginList(version: Int, hash: String = sha256(archive)): String = """[
        {"url":"https://example.com/A.cs3","version":$version,"name":"A","internalName":"A",
         "tvTypes":["Anime"],"fileHash":"sha256-$hash"},
        {"url":"https://example.com/M.cs3","version":1,"name":"M","internalName":"M","tvTypes":["Movie"]}
    ]"""

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `the consent screen shows until the user accepts`() = runTest(dispatcher) {
        val model = viewModel()

        model.state.value.consentGiven shouldBe false
        model.giveConsent()
        model.state.value.consentGiven shouldBe true
    }

    @Test
    fun `adding a repository lists its anime plugins and counts the hidden ones`() = runTest(dispatcher) {
        val model = viewModel().apply { giveConsent() }

        model.addRepository(repo)

        model.state.value.repositories.map { it.url } shouldContainExactly listOf(repo)
        model.state.value.rows.items.map { it.internalName } shouldContainExactly listOf("A")
        model.state.value.rows.hiddenCount shouldBe 1
    }

    @Test
    fun `a refused repository shows a message and adds nothing`() = runTest(dispatcher) {
        val model = viewModel().apply { giveConsent() }

        model.addRepository("http://example.com/repo.json")

        model.state.value.repositories.shouldBeEmpty()
        model.state.value.message shouldNotBe null
        model.dismissMessage()
        model.state.value.message shouldBe null
    }

    @Test
    fun `installing a plugin marks it installed`() = runTest(dispatcher) {
        val model = viewModel().apply {
            giveConsent()
            addRepository(repo)
        }

        model.install("A")

        model.state.value.rows.items.single().installed?.version shouldBe 1
        model.state.value.busy.shouldBeEmpty()
    }

    @Test
    fun `a plugin that fails its hash check is not installed`() = runTest(dispatcher) {
        texts[listUrl] = pluginList(version = 1, hash = "0".repeat(64))
        val model = viewModel().apply {
            giveConsent()
            addRepository(repo)
        }

        model.install("A")

        model.state.value.rows.items.single().installed shouldBe null
        model.state.value.message shouldNotBe null
    }

    @Test
    fun `update all installs every newer version`() = runTest(dispatcher) {
        val model = viewModel().apply {
            giveConsent()
            addRepository(repo)
            install("A")
        }
        texts[listUrl] = pluginList(version = 2)
        model.refresh()
        model.state.value.rows.updateCount shouldBe 1

        model.updateAll()

        model.state.value.rows.items.single().installed?.version shouldBe 2
        model.state.value.rows.updateCount shouldBe 0
    }

    @Test
    fun `a repository that fails to load is reported by name`() = runTest(dispatcher) {
        val model = viewModel().apply {
            giveConsent()
            addRepository(repo)
        }
        texts.remove(listUrl)

        model.refresh()

        model.state.value.repositoryErrors.keys shouldContainExactly setOf(repo)
    }

    @Test
    fun `removing a repository removes its plugins`() = runTest(dispatcher) {
        val model = viewModel().apply {
            giveConsent()
            addRepository(repo)
            install("A")
        }

        model.removeRepository(repo)

        model.state.value.repositories.shouldBeEmpty()
        model.state.value.rows.items.shouldBeEmpty()
    }

    @Test
    fun `disabling a plugin keeps it installed`() = runTest(dispatcher) {
        val model = viewModel().apply {
            giveConsent()
            addRepository(repo)
            install("A")
        }

        model.setEnabled("A", false)

        model.state.value.rows.items.single().installed?.enabled shouldBe false
    }
}
