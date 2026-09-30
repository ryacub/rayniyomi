package eu.kanade.tachiyomi.source

/**
 * The base source interface of extensions-lib 1.6. This fork names that interface [MangaSource].
 *
 * Some lib-1.6 extensions call `Source.getSupportsLatest()` on themselves. Without this type,
 * the call throws NoClassDefFoundError, and the extension operation fails (R1078).
 */
interface Source {

    /**
     * Whether the source has support for latest updates.
     */
    val supportsLatest: Boolean
}
