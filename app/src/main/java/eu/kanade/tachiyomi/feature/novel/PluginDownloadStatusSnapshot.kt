package eu.kanade.tachiyomi.feature.novel

import eu.kanade.tachiyomi.data.download.model.DownloadDisplayStatus
import eu.kanade.tachiyomi.data.download.model.DownloadStatusSnapshot

/**
 * Adapter contract for mapping light novel plugin download lifecycle into shared
 * download status UI/notification semantics.
 */
data class PluginDownloadStatusSnapshot(
    override val isRunningTransfer: Boolean,
    override val displayStatus: DownloadDisplayStatus,
    override val lastProgressAt: Long = 0L,
    override val retryAttempt: Int = 0,
    override val lastErrorReason: String? = null,
) : DownloadStatusSnapshot
