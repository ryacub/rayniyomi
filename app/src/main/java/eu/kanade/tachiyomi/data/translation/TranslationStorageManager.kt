package eu.kanade.tachiyomi.data.translation

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.source.MangaSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Manages storage of translated manga page images.
 *
 * Uses the convention: `<chapter_dir>/_translated/<lang>/` for storing overlaid images,
 * with a `.translation_meta` JSON file containing metadata.
 */
class TranslationStorageManager(
    private val downloadProvider: MangaDownloadProvider,
) {

    private val json = Json { prettyPrint = true }

    /**
     * Get (or create) the translated images directory for a chapter and language.
     */
    fun getTranslatedDir(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        source: MangaSource,
        targetLang: String,
    ): UniFile? {
        val chapterDir = downloadProvider.findChapterDir(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
        ) ?: return null

        return chapterDir.createDirectory(TRANSLATED_DIR)
            ?.createDirectory(targetLang)
    }

    /**
     * Check if a chapter has translations for the given language.
     */
    fun isChapterTranslated(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        source: MangaSource,
        targetLang: String,
    ): Boolean {
        val chapterDir = downloadProvider.findChapterDir(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
        ) ?: return false

        val translatedDir = chapterDir.findFile(TRANSLATED_DIR)
            ?.findFile(targetLang) ?: return false

        return translatedDir.listFiles()?.any { it.isFile } == true
    }

    /**
     * Get the translated file for a specific page index, or null if not translated.
     */
    fun getTranslatedPageFile(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        source: MangaSource,
        targetLang: String,
        pageIndex: Int,
    ): UniFile? {
        val chapterDir = downloadProvider.findChapterDir(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
        ) ?: return null

        val translatedDir = chapterDir.findFile(TRANSLATED_DIR)
            ?.findFile(targetLang) ?: return null

        // Match file by page index prefix (e.g., "001.jpg", "001.png")
        val prefix = "%03d.".format(pageIndex + 1)
        return translatedDir.listFiles()
            ?.firstOrNull { it.isFile && it.name?.startsWith(prefix) == true }
    }

    /**
     * Write a translated page image to storage.
     */
    fun writeTranslatedPage(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        source: MangaSource,
        targetLang: String,
        fileName: String,
        imageBytes: ByteArray,
    ): UniFile? {
        val dir = getTranslatedDir(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
            targetLang,
        ) ?: return null

        val file = dir.createFile(fileName) ?: return null
        file.openOutputStream().use { it.write(imageBytes) }
        return file
    }

    /**
     * Write translation metadata.
     */
    fun writeMetadata(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        source: MangaSource,
        targetLang: String,
        provider: String,
    ) {
        val dir = getTranslatedDir(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
            targetLang,
        ) ?: return

        val meta = TranslationMetadata(
            provider = provider,
            timestamp = System.currentTimeMillis(),
            targetLanguage = targetLang,
        )

        val metaFile = dir.createFile(META_FILE) ?: return
        metaFile.openOutputStream().use {
            it.write(json.encodeToString(meta).toByteArray())
        }
    }

    /**
     * Delete translations for a chapter in the given language.
     */
    fun deleteTranslation(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        source: MangaSource,
        targetLang: String,
    ): Boolean {
        val chapterDir = downloadProvider.findChapterDir(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
        ) ?: return false

        val translatedDir = chapterDir.findFile(TRANSLATED_DIR)
            ?.findFile(targetLang) ?: return false

        return translatedDir.delete()
    }

    /**
     * Delete all translations for a chapter (all languages).
     */
    fun deleteAllTranslations(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        source: MangaSource,
    ): Boolean {
        val chapterDir = downloadProvider.findChapterDir(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
        ) ?: return false

        val translatedDir = chapterDir.findFile(TRANSLATED_DIR) ?: return false
        return translatedDir.delete()
    }

    @Serializable
    data class TranslationMetadata(
        val provider: String,
        val timestamp: Long,
        val targetLanguage: String,
    )

    companion object {
        const val TRANSLATED_DIR = "_translated"
        private const val META_FILE = ".translation_meta"
    }
}
