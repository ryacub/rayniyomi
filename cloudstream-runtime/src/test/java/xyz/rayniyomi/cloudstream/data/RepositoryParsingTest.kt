package xyz.rayniyomi.cloudstream.data

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RepositoryParsingTest {

    @Test
    fun `reads the re-3arabi repository manifest`() {
        val manifest = parseRepositoryManifest(RE3ARABI_REPO_JSON)

        manifest.name shouldBe "3rabi عربي"
        manifest.manifestVersion shouldBe 1
        manifest.pluginLists shouldContainExactly
            listOf("https://raw.githubusercontent.com/Abodabodd/re-3arabi/builds/plugins.json")
    }

    @Test
    fun `rejects a manifest without plugin lists`() {
        shouldThrowAny { parseRepositoryManifest("""{"name":"Empty","manifestVersion":1}""") }
    }

    @Test
    fun `reads plugin list entries and ignores unknown fields`() {
        val entries = parsePluginList(PLUGINS_JSON)

        entries.map { it.internalName } shouldContainExactly listOf("Anime4up", "Animewitcher", "Broken")
        val anime4up = entries.first()
        anime4up.url shouldBe "https://raw.githubusercontent.com/Abodabodd/re-3arabi/builds/Anime4up.cs3"
        anime4up.version shouldBe 1
        anime4up.status shouldBe 1
        anime4up.language shouldBe "ar"
        anime4up.authors shouldContainExactly listOf("Abodabodd")
        anime4up.tvTypes shouldContainExactly listOf("TvSeries", "Movie", "Anime")
        anime4up.sha256 shouldBe "30ae4aaab6596ed950a55d5a41dfd44d42721c7d1e9af0fea2eabc61a40ece06"
    }

    @Test
    fun `a missing hash and missing types stay empty`() {
        val broken = parsePluginList(PLUGINS_JSON).last()

        broken.sha256 shouldBe null
        broken.tvTypes shouldBe emptyList()
    }

    @Test
    fun `only anime, anime movie, and OVA plugins are anime compatible`() {
        val entries = parsePluginList(PLUGINS_JSON).associateBy { it.internalName }

        entries.getValue("Anime4up").isAnimeCompatible shouldBe true
        entries.getValue("Animewitcher").isAnimeCompatible shouldBe true
        entries.getValue("Broken").isAnimeCompatible shouldBe false
        entry(tvTypes = listOf("OVA")).isAnimeCompatible shouldBe true
        entry(tvTypes = listOf("Movie", "TvSeries")).isAnimeCompatible shouldBe false
        entry(tvTypes = listOf("tvTypes", "live")).isAnimeCompatible shouldBe false
    }

    private fun entry(tvTypes: List<String>) = parsePluginList(PLUGINS_JSON).first().copy(tvTypes = tvTypes)

    private companion object {
        const val RE3ARABI_REPO_JSON = """
            {
                "name": "3rabi عربي",
                "description": "مشاهدة الافلام والمسلسلات تجربة خالية من الاعلانات",
                "iconUrl": "https://raw.githubusercontent.com/Abodabodd/re-3arabi/refs/heads/main/icon.png",
                "manifestVersion": 1,
                "pluginLists": [
                    "https://raw.githubusercontent.com/Abodabodd/re-3arabi/builds/plugins.json"
                ]
            }
        """

        const val PLUGINS_JSON = """
            [
                {
                    "url": "https://raw.githubusercontent.com/Abodabodd/re-3arabi/builds/Anime4up.cs3",
                    "status": 1,
                    "version": 1,
                    "name": "Anime4up",
                    "internalName": "Anime4up",
                    "authors": ["Abodabodd"],
                    "description": "",
                    "fileSize": 37890,
                    "repositoryUrl": "https://github.com/Abodabodd/re-3arabi",
                    "language": "ar",
                    "tvTypes": ["TvSeries", "Movie", "Anime"],
                    "iconUrl": "https://example.com/icon.png",
                    "apiVersion": 1,
                    "fileHash": "sha256-30ae4aaab6596ed950a55d5a41dfd44d42721c7d1e9af0fea2eabc61a40ece06",
                    "unknownField": {"nested": true}
                },
                {
                    "url": "https://raw.githubusercontent.com/Abodabodd/re-3arabi/builds/Animewitcher.cs3",
                    "status": 1,
                    "version": 3,
                    "name": "Animewitcher",
                    "internalName": "Animewitcher",
                    "language": "ar",
                    "tvTypes": ["Anime", "AnimeMovie"],
                    "apiVersion": 1,
                    "fileHash": "sha256-4c639f93aaaa"
                },
                {
                    "url": "https://example.com/Broken.cs3",
                    "status": 0,
                    "version": 2,
                    "name": "Broken",
                    "internalName": "Broken",
                    "tvTypes": null
                }
            ]
        """
    }
}
