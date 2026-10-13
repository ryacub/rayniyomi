package xyz.rayniyomi.cloudstream.data

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class PluginStateTest {

    private val repo = "https://raw.githubusercontent.com/Abodabodd/re-3arabi/builds/repo.json"
    private val other = "https://example.com/repo.json"

    private fun entry(name: String, version: Int = 1, hash: String? = "aa$name", tvTypes: List<String> = listOf("Anime")) =
        PluginEntry(
            url = "https://example.com/$name.cs3",
            status = 1,
            version = version,
            name = name,
            internalName = name,
            language = "ar",
            tvTypes = tvTypes,
            fileHash = hash?.let { "sha256-$it" },
        )

    private val consented = PluginState().giveConsent().addRepository(repo, "3rabi").addRepository(other, "Other")

    @Test
    fun `a repository URL must use HTTPS`() {
        shouldThrow<IllegalArgumentException> { PluginState().addRepository("http://example.com/repo.json", "Bad") }
    }

    @Test
    fun `adding the same repository twice keeps one entry`() {
        consented.addRepository(repo, "3rabi").repositories.map { it.url } shouldContainExactly listOf(repo, other)
    }

    @Test
    fun `an installed plugin starts enabled`() {
        val state = consented.install(entry("A"), repo, "aaA")

        state.installed.single().enabled shouldBe true
        state.installed.single().fileSha256 shouldBe "aaA"
    }

    @Test
    fun `installing a newer version replaces the old record`() {
        val state = consented.install(entry("A"), repo, "aaA").install(entry("A", version = 2), repo, "bbA")

        state.installed.single().version shouldBe 2
        state.installed.single().fileSha256 shouldBe "bbA"
    }

    @Test
    fun `an update exists for a higher remote version only`() {
        val installed = consented.install(entry("A", version = 2), repo, "aaA").installed.single()

        installed.hasUpdate(entry("A", version = 3)) shouldBe true
        installed.hasUpdate(entry("A", version = 2)) shouldBe false
        installed.hasUpdate(entry("A", version = 1)) shouldBe false
    }

    @Test
    fun `an always-update plugin has an update only when its hash changes`() {
        val installed = consented.install(entry("A", version = -1, hash = "aaA"), repo, "aaA").installed.single()

        installed.hasUpdate(entry("A", version = -1, hash = "aaA")) shouldBe false
        installed.hasUpdate(entry("A", version = -1, hash = "ccA")) shouldBe true
    }

    @Test
    fun `a disabled plugin is not offered to the host`() {
        val state = consented.install(entry("A"), repo, "aaA").install(entry("B"), repo, "aaB").setEnabled("A", false)

        state.providerPlugins().map { it.internalName } shouldContainExactly listOf("B")
    }

    @Test
    fun `no plugin is offered to the host before consent`() {
        val state = PluginState().addRepository(repo, "3rabi").install(entry("A"), repo, "aaA")

        state.providerPlugins().shouldBeEmpty()
        state.giveConsent().providerPlugins().map { it.internalName } shouldContainExactly listOf("A")
    }

    @Test
    fun `a plugin without anime types is not offered to the host`() {
        val state = consented.install(entry("M", tvTypes = listOf("Movie")), repo, "aaM")

        state.providerPlugins().shouldBeEmpty()
    }

    @Test
    fun `removing a plugin forgets it`() {
        consented.install(entry("A"), repo, "aaA").remove("A").installed.shouldBeEmpty()
    }

    @Test
    fun `removing a repository removes only its plugins`() {
        val state = consented.install(entry("A"), repo, "aaA").install(entry("B"), other, "aaB").removeRepository(repo)

        state.repositories.map { it.url } shouldContainExactly listOf(other)
        state.installed.map { it.internalName } shouldContainExactly listOf("B")
    }

    @Test
    fun `state survives an encode and decode round trip`() {
        val state = consented.install(entry("A"), repo, "aaA").setEnabled("A", false)

        PluginStateCodec.decode(PluginStateCodec.encode(state)) shouldBe state
    }

    @Test
    fun `an empty or corrupt state file gives the default state`() {
        PluginStateCodec.decode("") shouldBe PluginState()
        PluginStateCodec.decode("{not json") shouldBe PluginState()
    }

    @Test
    fun `an atomic write replaces the file and leaves no temp file`() {
        val dir = createTempDirectory("cs-state").toFile()
        try {
            val target = File(dir, "state.json").apply { writeText("old") }

            writeAtomically(target, "new".toByteArray())

            target.readText() shouldBe "new"
            dir.listFiles().orEmpty().map { it.name } shouldContainExactly listOf("state.json")
        } finally {
            dir.deleteRecursively()
        }
    }
}
