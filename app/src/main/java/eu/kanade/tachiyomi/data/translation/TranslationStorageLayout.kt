package eu.kanade.tachiyomi.data.translation

/**
 * Naming rules for translation output on disk.
 *
 * A chapter that is a folder of images keeps its translations in a child folder.
 * A chapter that is a .cbz archive cannot hold children, so its translations go
 * into a sibling folder next to the archive.
 */
object TranslationStorageLayout {

    /** Name of the translation folder inside a folder-of-images chapter. */
    const val TRANSLATED_DIR = "_translated"

    /**
     * Name of the sibling folder for an archive chapter, for example "Chapter 1.cbz_translated".
     */
    fun sidecarDirName(chapterFileName: String): String = "$chapterFileName$TRANSLATED_DIR"

    /**
     * True if a child of a manga folder is translation output and not a chapter.
     *
     * The rule requires the ".cbz" stem so a loose chapter directory whose name
     * happens to end in "_translated" is never hidden. Archive chapters are
     * always "<name>.cbz" files, so their sidecars always carry ".cbz_translated".
     */
    fun isSidecarDirName(name: String?): Boolean =
        name != null && name.endsWith(".cbz$TRANSLATED_DIR")
}
