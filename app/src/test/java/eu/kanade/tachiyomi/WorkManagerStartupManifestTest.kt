package eu.kanade.tachiyomi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class WorkManagerStartupManifestTest {

    @Test
    fun `work manager initializer is removed from startup provider`() {
        val manifest = loadManifest()

        val provider = manifest.provider("androidx.startup.InitializationProvider")
        val metaData = provider.metaData("androidx.work.WorkManagerInitializer")

        assertEquals("androidx.startup", metaData.getAttribute("android:value"))
        assertEquals("remove", metaData.getAttribute("tools:node"))
    }

    private fun loadManifest(): Element {
        val manifestFile = File("src/main/AndroidManifest.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        return document.documentElement
    }

    private fun Element.provider(name: String): Element {
        val providers = getElementsByTagName("provider")
        for (index in 0 until providers.length) {
            val provider = providers.item(index) as Element
            if (provider.getAttribute("android:name") == name) {
                return provider
            }
        }
        error("Provider $name not found in AndroidManifest.xml")
    }

    private fun Element.metaData(name: String): Element {
        val metaData = getElementsByTagName("meta-data")
        for (index in 0 until metaData.length) {
            val item = metaData.item(index) as Element
            if (item.getAttribute("android:name") == name) {
                return item
            }
        }
        error("Meta-data $name not found in AndroidManifest.xml")
    }
}