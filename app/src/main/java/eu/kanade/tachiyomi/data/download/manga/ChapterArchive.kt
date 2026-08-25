package eu.kanade.tachiyomi.data.download.manga

import mihon.core.archive.ArchiveReader
import java.io.Closeable
import java.io.InputStream

/** One entry of a downloaded chapter archive. */
class ChapterArchiveEntry(
    val name: String,
    val isFile: Boolean,
)

/**
 * Read access to one downloaded chapter archive. Mirrors mihon.core.archive.ArchiveReader.
 */
interface ChapterArchive : Closeable {
    fun <T> useEntries(block: (Sequence<ChapterArchiveEntry>) -> T): T
    fun getInputStream(entryName: String): InputStream?
}

internal class ChapterArchiveReaderAdapter(private val reader: ArchiveReader) : ChapterArchive {
    override fun <T> useEntries(block: (Sequence<ChapterArchiveEntry>) -> T): T {
        return reader.useEntries { entries ->
            block(entries.map { ChapterArchiveEntry(it.name, it.isFile) })
        }
    }

    override fun getInputStream(entryName: String): InputStream? = reader.getInputStream(entryName)

    override fun close() {
        reader.close()
    }
}
