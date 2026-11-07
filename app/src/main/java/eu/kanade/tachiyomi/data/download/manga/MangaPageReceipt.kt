package eu.kanade.tachiyomi.data.download.manga

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import eu.kanade.tachiyomi.source.model.Page
import okio.ByteString.Companion.encodeUtf8
import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.source
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

internal class MangaPageReceipt(cacheDirectory: File?, download: MangaDownload, page: Page) {
    private val receipt = cacheDirectory?.let {
        val chapterKey = "${download.source.id}-${download.manga.id}-${download.chapter.id}"
        File(it, "manga-page-receipts/$chapterKey/${page.index}")
    }
    private val identity = listOf(download.manga.url, download.chapter.url, page.url, page.imageUrl)
        .joinToString("") { value -> "${value?.length ?: -1}:${value.orEmpty()}" }
        .encodeUtf8().sha256().hex()

    fun matches(files: List<UniFile>): Boolean {
        val saved = receipt ?: return false
        if (!saved.isFile || files.isEmpty()) return false
        return try {
            saved.readText() == fingerprint(files)
        } catch (_: IOException) {
            false
        }
    }

    fun save(files: List<UniFile>) {
        val destination = receipt ?: return
        try {
            val parent = checkNotNull(destination.parentFile)
            parent.mkdirs()
            val temporary = File.createTempFile("page-", ".tmp", parent)
            try {
                temporary.writeText(fingerprint(files))
                Files.move(temporary.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            } finally {
                temporary.delete()
            }
        } catch (_: IOException) {
            // The current download can finish. A later attempt will fetch this page again.
        }
    }

    private fun fingerprint(files: List<UniFile>): String = buildString {
        appendLine(identity)
        files.forEach { file ->
            if (file.length() <= 0) throw IOException("Invalid staged page")
            val hash = HashingSource.sha256(file.openInputStream().source())
            hash.buffer().use { it.readAll(blackholeSink()) }
            appendLine("${file.name}:${file.length()}:${hash.hash.hex()}")
        }
    }
}
