package tachiyomi.domain.source.health

interface SourceHealthChecker {
    /** Perform the health probe. Throws on failure. */
    suspend fun probe(sourceId: Long)

    /** Return true to skip the health check (e.g. local/stub sources). */
    fun shouldSkip(sourceId: Long): Boolean
}
