package eu.kanade.tachiyomi.ui.deeplink

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class SearchIntentManifestTest {

    @Test
    fun `anime custom search action is only claimed by anime deep link activity`() {
        val manifest = loadManifest()

        val animeActivity = manifest.activity(".ui.deeplink.anime.DeepLinkAnimeActivity")
        val mangaActivity = manifest.activity(".ui.deeplink.manga.DeepLinkMangaActivity")

        assertTrue(animeActivity.claimsAction("eu.kanade.tachiyomi.ANIMESEARCH"))
        assertFalse(mangaActivity.claimsAction("eu.kanade.tachiyomi.ANIMESEARCH"))
    }

    @Test
    fun `manga custom search action is only claimed by manga deep link activity`() {
        val manifest = loadManifest()

        val animeActivity = manifest.activity(".ui.deeplink.anime.DeepLinkAnimeActivity")
        val mangaActivity = manifest.activity(".ui.deeplink.manga.DeepLinkMangaActivity")

        assertFalse(animeActivity.claimsAction("eu.kanade.tachiyomi.SEARCH"))
        assertTrue(mangaActivity.claimsAction("eu.kanade.tachiyomi.SEARCH"))
    }

    private fun loadManifest(): Element {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        return document.documentElement
    }

    private fun Element.activity(name: String): Element {
        val activities = getElementsByTagName("activity")
        for (index in 0 until activities.length) {
            val activity = activities.item(index) as Element
            if (activity.getAttribute("android:name") == name) {
                return activity
            }
        }
        error("Activity $name not found in AndroidManifest.xml")
    }

    private fun Element.claimsAction(actionName: String): Boolean {
        val actions = getElementsByTagName("action")
        for (index in 0 until actions.length) {
            val action = actions.item(index) as Element
            if (action.getAttribute("android:name") == actionName) {
                return true
            }
        }
        return false
    }
}
