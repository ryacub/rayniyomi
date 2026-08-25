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
 * A chapter that is a folder of images keeps its translations in a child folder:
 * `<chapter_dir>/_translated/<lang>/`.
 * A chapter that is a .cbz archive cannot hold children, so its translations go
 * into a sibling folder next to the archive:
 * `<manga_dir>/<chapter_file>_translated/<lang>/`.
 * Both layouts keep a `.translation_meta` JSON file inside the language folder.
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
        val root = getTranslationRoot(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
            create = true,
        ) ?: return null

        return root.createDirectory(targetLang)
    }

    /**
     * Get the folder that holds all translation languages for a chapter.
     *
     * @param create true to make the folder if it does not exist.
     */
    private fun getTranslationRoot(
        chapterName: String,
        chapterScanlator: String?,
        mangaTitle: String,
        source: MangaSource,
        create: Boolean,
    ): UniFile? {
        val chapterDir = downloadProvider.findChapterDir(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
        ) ?: return null

        if (!chapterDir.isFile) {
            // A folder of images holds its translations inside itself.
            return if (create) {
                chapterDir.createDirectory(TranslationStorageLayout.TRANSLATED_DIR)
            } else {
                chapterDir.findFile(TranslationStorageLayout.TRANSLATED_DIR)
            }
        }

        // An archive cannot hold children. Use a sibling folder in the manga folder.
        val mangaDir = downloadProvider.findMangaDir(mangaTitle, source) ?: return null
        val sidecarName = TranslationStorageLayout.sidecarDirName(chapterDir.name ?: return null)
        return if (create) mangaDir.createDirectory(sidecarName) else mangaDir.findFile(sidecarName)
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
        val root = getTranslationRoot(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
            create = false,
        ) ?: return false

        val translatedDir = root.findFile(targetLang) ?: return false

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
        val root = getTranslationRoot(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
            create = false,
        ) ?: return null

        val translatedDir = root.findFile(targetLang) ?: return null

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
        val root = getTranslationRoot(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
            create = false,
        ) ?: return false

        val translatedDir = root.findFile(targetLang) ?: return false

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
        val root = getTranslationRoot(
            chapterName,
            chapterScanlator,
            mangaTitle,
            source,
            create = false,
        ) ?: return false

        return root.delete()
    }

    @Serializable
    data class TranslationMetadata(
        val provider: String,
        val timestamp: Long,
        val targetLanguage: String,
    )

    companion object {
        private const val META_FILE = ".translation_meta"
    }
}
