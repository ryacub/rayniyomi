package eu.kanade.tachiyomi.data.download.core

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.size
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
internal class StoredDirSize(
    @Volatile private var bytes: Long? = null,
) {
    @Transient
    private var generation = 0

    fun getOrWalk(dir: UniFile?, onStored: () -> Unit): Long {
        bytes?.let { return it }

        val walkGeneration = synchronized(this) { generation }
        val walked = dir?.listFiles()
            ?.filterNot { it.name?.endsWith(PARTIAL_DOWNLOAD_SUFFIX) == true }
            ?.sumOf { it.size().coerceAtLeast(0) }
            ?: 0
        if (storeIfUnchangedSince(walkGeneration, walked)) onStored()
        return walked
    }

    @Synchronized
    fun clear() {
        bytes = null
        generation++
    }

    @Synchronized
    private fun storeIfUnchangedSince(walkGeneration: Int, walked: Long): Boolean {
        if (walkGeneration != generation) return false
        bytes = walked
        return true
    }

    private companion object {
        const val PARTIAL_DOWNLOAD_SUFFIX = "_tmp"
    }
}
