package eu.kanade.tachiyomi.feature.novel

import xyz.rayniyomi.lightnovel.contract.LightNovelTransferDisplayStatus
import xyz.rayniyomi.lightnovel.contract.LightNovelTransferSnapshot

/**
 * Host-side bridge point for future plugin import status projection.
 *
 * This keeps host/install state and plugin/import state intentionally decoupled.
 */
data class HostLightNovelTransferSnapshot(
    override val displayStatus: LightNovelTransferDisplayStatus,
    override val lastProgressAt: Long = 0L,
    override val retryAttempt: Int = 0,
    override val lastErrorReason: String? = null,
    override val progressPercent: Int? = null,
) : LightNovelTransferSnapshot
