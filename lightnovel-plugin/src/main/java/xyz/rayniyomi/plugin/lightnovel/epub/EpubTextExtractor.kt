package xyz.rayniyomi.plugin.lightnovel.epub

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
}
