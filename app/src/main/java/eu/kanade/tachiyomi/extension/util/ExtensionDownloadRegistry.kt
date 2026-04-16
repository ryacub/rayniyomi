package eu.kanade.tachiyomi.extension.util

import eu.kanade.tachiyomi.extension.InstallStep
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

internal class ExtensionDownloadRegistry {

    data class ActiveDownload(
        val downloadId: Long,
        val stateFlow: MutableStateFlow<InstallStep>,
    )

    private val packageDownloads = ConcurrentHashMap<String, ActiveDownload>()
    private val downloadsStateFlows = ConcurrentHashMap<Long, MutableStateFlow<InstallStep>>()

    fun getOrCreate(pkgName: String, createDownload: () -> Long): ActiveDownload {
        return packageDownloads.compute(pkgName) { _, current ->
            current ?: run {
                val id = createDownload()
                val stateFlow = MutableStateFlow(InstallStep.Pending)
                val activeDownload = ActiveDownload(id, stateFlow)
                downloadsStateFlows[id] = stateFlow
                activeDownload
            }
        }!!
    }

    fun updateInstallStep(downloadId: Long, step: InstallStep) {
        downloadsStateFlows[downloadId]?.value = step
    }

    fun containsDownloadId(downloadId: Long): Boolean {
        return downloadsStateFlows.containsKey(downloadId)
    }

    fun removeCurrent(pkgName: String): ActiveDownload? {
        return removeIfMatch(pkgName, expectedId = null)
    }

    fun removeIfMatch(pkgName: String, expectedId: Long?): ActiveDownload? {
        var removed: ActiveDownload? = null
        packageDownloads.compute(pkgName) { _, current ->
            if (current == null) {
                null
            } else if (expectedId != null && current.downloadId != expectedId) {
                current
            } else {
                removed = current
                null
            }
        }

        removed?.let { downloadsStateFlows.remove(it.downloadId) }
        return removed
    }

    fun isEmpty(): Boolean {
        return packageDownloads.isEmpty()
    }
}
