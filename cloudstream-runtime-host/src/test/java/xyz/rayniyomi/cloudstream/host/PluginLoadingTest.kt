package xyz.rayniyomi.cloudstream.host

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory

class PluginLoadingTest {

    @Test
    fun `one failing plugin does not stop the others`() {
        val result = loadIsolated(listOf("a", "b", "c", "d"), name = { it }) { item ->
            when (item) {
                "b" -> throw NoSuchMethodError("newEpisode")
                "c" -> throw NotImplementedError()
                else -> listOf("$item-source")
            }
        }

        result.loaded shouldContainExactly listOf("a-source", "d-source")
        result.failures.map { it.first } shouldContainExactly listOf("b", "c")
    }

    @Test
    fun `a cs3 manifest gives the plugin class, version, and resource flag`() {
        withCs3("""{"pluginClassName":"com.animewitcher.animewitcherPlugin","name":"Animewitcher","version":3}""") {
            val manifest = Cs3Manifest.read(it)

            manifest.pluginClassName shouldBe "com.animewitcher.animewitcherPlugin"
            manifest.name shouldBe "Animewitcher"
            manifest.version shouldBe 3
            manifest.requiresResources shouldBe false
        }
    }

    @Test
    fun `a cs3 without a plugin class is rejected`() {
        withCs3("""{"name":"Broken","version":1,"requiresResources":true}""") {
            shouldThrow<IllegalArgumentException> { Cs3Manifest.read(it) }
        }
    }

    private fun withCs3(manifest: String, block: (File) -> Unit) {
        val dir = createTempDirectory("cs3").toFile()
        try {
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("classes.dex"))
                zip.write(byteArrayOf(0))
                zip.closeEntry()
            }
            val file = File(dir, "plugin.cs3").apply { writeBytes(bytes.toByteArray()) }
            block(file)
        } finally {
            dir.deleteRecursively()
        }
    }
}
