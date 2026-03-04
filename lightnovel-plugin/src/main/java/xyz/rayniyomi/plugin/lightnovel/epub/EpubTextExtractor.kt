package xyz.rayniyomi.plugin.lightnovel.epub

import android.graphics.BitmapFactory
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

data class EpubChapter(
    val title: String,
    val text: String,
)

data class EpubContent(
    val title: String,
    val chapters: List<EpubChapter>,
)

object EpubTextExtractor {
    fun parse(file: File): EpubContent {
        ZipFile(file).use { zip ->
            val packagePath = findPackagePath(zip)
            val opfDoc = parseXml(zip, packagePath)
            val epubTitle = findBookTitle(opfDoc) ?: file.nameWithoutExtension
            val chapterPaths = findChapterPaths(opfDoc)
            val opfBasePath = packagePath.substringBeforeLast('/', "")

            val chapters = chapterPaths.mapIndexedNotNull { index, href ->
                val resolvedPath = resolvePath(opfBasePath, href)
                val htmlEntry = zip.getEntry(resolvedPath) ?: return@mapIndexedNotNull null
                val chapterDoc = htmlEntry.getInputStream(zip).use {
                    Jsoup.parse(it.readBytes().toString(StandardCharsets.UTF_8), "", Parser.xmlParser())
                }
                val chapterText = chapterDoc.body()?.text()?.trim().orEmpty()
                if (chapterText.isBlank()) {
                    null
                } else {
                    val chapterTitle = chapterDoc.title().ifBlank { "Chapter ${index + 1}" }
                    EpubChapter(title = chapterTitle, text = chapterText)
                }
            }

            return EpubContent(
                title = epubTitle,
                chapters = chapters,
            )
        }
    }

    fun extractCoverSeedColor(file: File): Int? {
        ZipFile(file).use { zip ->
            val packagePath = findPackagePath(zip)
            val opfDoc = parseXml(zip, packagePath)
            val coverPath = findCoverPath(opfDoc) ?: return null
            val opfBasePath = packagePath.substringBeforeLast('/', "")
            val resolvedPath = resolvePath(opfBasePath, coverPath)
            val coverEntry = zip.getEntry(resolvedPath) ?: return null

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                inSampleSize = 4
            }
            val bitmap = coverEntry.getInputStream(zip).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null

            return try {
                averageColor(bitmap)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun findPackagePath(zip: ZipFile): String {
        val container = zip.getEntry("META-INF/container.xml")
            ?: return "OEBPS/content.opf"
        val doc = container.getInputStream(zip).use {
            Jsoup.parse(it.readBytes().toString(StandardCharsets.UTF_8), "", Parser.xmlParser())
        }
        return doc.selectFirst("rootfile")?.attr("full-path")
            ?.takeIf { it.isNotBlank() }
            ?: "OEBPS/content.opf"
    }

    private fun parseXml(zip: ZipFile, entryPath: String) =
        zip.getEntry(entryPath)?.getInputStream(zip)?.use {
            Jsoup.parse(it.readBytes().toString(StandardCharsets.UTF_8), "", Parser.xmlParser())
        } ?: error("Missing OPF package document: $entryPath")

    private fun findBookTitle(opfDoc: org.jsoup.nodes.Document): String? {
        return opfDoc.select("metadata > *")
            .firstOrNull { it.tagName().endsWith("title") }
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun findChapterPaths(opfDoc: org.jsoup.nodes.Document): List<String> {
        val manifest = opfDoc.select("manifest > item")
            .associateBy(
                keySelector = { it.attr("id") },
                valueTransform = { it.attr("href") },
            )
        val spine = opfDoc.select("spine > itemref")
            .map { it.attr("idref") }
        return spine.mapNotNull { manifest[it] }
    }

    private fun findCoverPath(opfDoc: org.jsoup.nodes.Document): String? {
        val manifestItems = opfDoc.select("manifest > item")
        val metadataCoverId = opfDoc.select("metadata > meta")
            .firstOrNull { it.attr("name").equals("cover", ignoreCase = true) }
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }

        if (!metadataCoverId.isNullOrBlank()) {
            manifestItems.firstOrNull { it.attr("id") == metadataCoverId }
                ?.attr("href")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        manifestItems.firstOrNull { it.attr("properties").contains("cover-image") }
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        return manifestItems.firstOrNull {
            val id = it.attr("id")
            val href = it.attr("href")
            id.contains("cover", ignoreCase = true) || href.contains("cover", ignoreCase = true)
        }?.attr("href")?.takeIf { it.isNotBlank() }
    }

    private fun resolvePath(basePath: String, href: String): String {
        if (href.startsWith('/')) {
            return href.removePrefix("/")
        }

        val baseParts = basePath.split('/').filter { it.isNotBlank() }.toMutableList()
        val pathParts = href.split('/')

        pathParts.forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (baseParts.isNotEmpty()) baseParts.removeAt(baseParts.lastIndex)
                else -> baseParts.add(part)
            }
        }

        return baseParts.joinToString("/")
    }

    private fun java.util.zip.ZipEntry.getInputStream(zip: ZipFile) = zip.getInputStream(this)

    private fun averageColor(bitmap: android.graphics.Bitmap): Int? {
        val width = bitmap.width.coerceAtLeast(1)
        val height = bitmap.height.coerceAtLeast(1)
        val stepX = (width / 24).coerceAtLeast(1)
        val stepY = (height / 24).coerceAtLeast(1)

        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var count = 0L

        var x = 0
        while (x < width) {
            var y = 0
            while (y < height) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(pixel) >= 128) {
                    sumR += android.graphics.Color.red(pixel)
                    sumG += android.graphics.Color.green(pixel)
                    sumB += android.graphics.Color.blue(pixel)
                    count++
                }
                y += stepY
            }
            x += stepX
        }

        if (count == 0L) return null
        val r = (sumR / count).toInt().coerceIn(0, 255)
        val g = (sumG / count).toInt().coerceIn(0, 255)
        val b = (sumB / count).toInt().coerceIn(0, 255)
        return android.graphics.Color.rgb(r, g, b)
    }
}
