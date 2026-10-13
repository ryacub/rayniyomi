package xyz.rayniyomi.cloudstream.data

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PluginArchiveTest {

    private fun zip(vararg names: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            names.forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(if (name == "manifest.json") MANIFEST.toByteArray() else byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `a zip with a manifest and dex that matches its hash is valid`() {
        val archive = zip("manifest.json", "classes.dex")

        checkPluginArchive(archive, sha256(archive)) shouldBe PluginArchiveCheck.Valid(sha256(archive))
    }

    @Test
    fun `a hash mismatch is rejected`() {
        val archive = zip("manifest.json", "classes.dex")

        checkPluginArchive(archive, "00".repeat(32)) shouldBe PluginArchiveCheck.HashMismatch
    }

    @Test
    fun `the hash check ignores letter case`() {
        val archive = zip("manifest.json", "classes.dex")

        checkPluginArchive(archive, sha256(archive).uppercase()) shouldBe PluginArchiveCheck.Valid(sha256(archive))
    }

    @Test
    fun `an archive without a published hash is rejected`() {
        checkPluginArchive(zip("manifest.json", "classes.dex"), null) shouldBe PluginArchiveCheck.MissingHash
    }

    @Test
    fun `bytes that are not a zip are rejected`() {
        val bytes = "<html>Not Found</html>".toByteArray()

        checkPluginArchive(bytes, sha256(bytes)) shouldBe PluginArchiveCheck.NotAPlugin
    }

    @Test
    fun `a zip without a manifest or dex is rejected`() {
        val noManifest = zip("classes.dex")
        val noDex = zip("manifest.json")

        checkPluginArchive(noManifest, sha256(noManifest)) shouldBe PluginArchiveCheck.NotAPlugin
        checkPluginArchive(noDex, sha256(noDex)) shouldBe PluginArchiveCheck.NotAPlugin
    }

    @Test
    fun `only HTTPS URLs are accepted`() {
        isHttpsUrl("https://raw.githubusercontent.com/a/b/repo.json") shouldBe true
        isHttpsUrl("HTTPS://example.com/x") shouldBe true
        isHttpsUrl("http://example.com/x") shouldBe false
        isHttpsUrl("file:///sdcard/x.cs3") shouldBe false
        isHttpsUrl("https://") shouldBe false
        isHttpsUrl("not a url") shouldBe false
    }

    private companion object {
        const val MANIFEST = """{"pluginClassName":"com.example.ExamplePlugin","name":"Example","version":1}"""
    }
}
