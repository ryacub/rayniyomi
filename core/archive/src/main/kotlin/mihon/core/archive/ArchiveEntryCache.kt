package mihon.core.archive

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * A solid archive (7z, RAR, compressed tar) decodes every earlier entry to reach one entry. This cache decodes
 * each entry once, in archive order, to disk. Other archives read from [reader]. [close] also closes [reader].
 */
class ArchiveEntryCache(private val reader: ArchiveReader) : Closeable {

    private val lock = ReentrantLock()
    private val extracted = ConcurrentHashMap<String, File>()

    @Volatile
    private var closed = false
    private var released = false

    private var needsDecodeToSkip: Boolean? = null
    private var cursor: ArchiveInputStream? = null
    private var cursorIndex = 0
    private var exhausted = false
    private var directory: File? = null

    fun getInputStream(entryName: String): InputStream? {
        extracted[entryName]?.let { return it.inputStream() }
        return lock.withLock {
            try {
                if (closed) throw IOException("Archive is closed")
                val decodeToSkip = needsDecodeToSkip
                    ?: reader.openStream().use { it.getNextEntry(); it.needsDecodeToSkip() }
                        .also { needsDecodeToSkip = it }
                if (decodeToSkip) extract(entryName)?.inputStream() else reader.getInputStream(entryName)
            } finally {
                if (closed) release()
            }
        }
    }

    private fun extract(entryName: String): File? {
        extracted[entryName]?.let { return it }
        if (exhausted) return null

        val directory = directory ?: createDirectory().also { directory = it }
        val stream = cursor ?: reader.openStream().also {
            cursor = it
            cursorIndex = 0
        }
        try {
            while (true) {
                if (closed) throw IOException("Archive is closed")
                val entry = stream.getNextEntry() ?: break
                val index = cursorIndex++
                if (!entry.isFile || extracted.containsKey(entry.name)) continue

                val file = File(directory, "entry-$index")
                try {
                    file.outputStream().use { stream.copyTo(it) }
                } catch (e: Exception) {
                    file.delete()
                    throw e
                }
                extracted[entry.name] = file
                if (entry.name == entryName) return file
            }
        } catch (e: Exception) {
            stream.close()
            cursor = null
            throw e
        }

        stream.close()
        cursor = null
        exhausted = true
        return null
    }

    private fun release() {
        if (released) return
        released = true
        cursor?.close()
        cursor = null
        directory?.deleteRecursively()
        extracted.clear()
        reader.close()
    }

    override fun close() {
        closed = true
        if (lock.tryLock()) {
            try {
                release()
            } finally {
                lock.unlock()
            }
        }
    }

    private companion object {
        private var staleEntriesCleared = false

        @Synchronized
        fun createDirectory(): File {
            val parent = File(System.getProperty("java.io.tmpdir"), "archive-cache")
            if (!staleEntriesCleared) {
                parent.deleteRecursively()
                staleEntriesCleared = true
            }
            parent.mkdirs()
            return Files.createTempDirectory(parent.toPath(), "entries").toFile()
        }
    }
}
