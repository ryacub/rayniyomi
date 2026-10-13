package xyz.rayniyomi.cloudstream.data

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PluginRowsTest {

    private val repo = Repository("https://example.com/repo.json", "Example")

    private fun entry(name: String, version: Int = 1, tvTypes: List<String> = listOf("Anime")) = PluginEntry(
        url = "https://example.com/$name.cs3",
        version = version,
        name = name,
        internalName = name,
        tvTypes = tvTypes,
        fileHash = "sha256-aa",
    )

    @Test
    fun `rows list anime plugins by name and count the hidden ones`() {
        val rows = buildPluginRows(
            PluginState(consentGiven = true, repositories = listOf(repo)),
            listOf(AvailablePlugins(repo, listOf(entry("beta"), entry("Movie", tvTypes = listOf("Movie")), entry("Alpha")))),
        )

        rows.items.map { it.name } shouldContainExactly listOf("Alpha", "beta")
        rows.hiddenCount shouldBe 1
    }

    @Test
    fun `an installed plugin shows its update`() {
        val state = PluginState(consentGiven = true, repositories = listOf(repo)).install(entry("A"), repo.url, "aa")

        val rows = buildPluginRows(state, listOf(AvailablePlugins(repo, listOf(entry("A", version = 2)))))

        rows.items.single().installed?.version shouldBe 1
        rows.items.single().updateAvailable shouldBe true
        rows.updateCount shouldBe 1
    }

    @Test
    fun `an installed plugin stays listed when its repository does not load`() {
        val state = PluginState(consentGiven = true, repositories = listOf(repo)).install(entry("A"), repo.url, "aa")

        val rows = buildPluginRows(state, listOf(AvailablePlugins(repo, emptyList(), error = "HTTP 503")))

        rows.items.map { it.name } shouldContainExactly listOf("A")
        rows.items.single().available shouldBe null
        rows.items.single().updateAvailable shouldBe false
    }
}
