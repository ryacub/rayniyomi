package xyz.rayniyomi.plugin.lightnovel.data

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import xyz.rayniyomi.lightnovel.contract.LightNovelTransferDisplayStatus
import xyz.rayniyomi.lightnovel.contract.LightNovelTransferSnapshot

data class NovelImportStatus(
    override val displayStatus: LightNovelTransferDisplayStatus =
        LightNovelTransferDisplayStatus.PREPARING,
    override val lastProgressAt: Long = 0L,
    override val retryAttempt: Int = 0,
    override val lastErrorReason: String? = null,
    override val progressPercent: Int? = null,
) : LightNovelTransferSnapshot

class NovelImportStatusTracker(
    private val nowMs: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private var latestProgressAtMs: Long = 0L

    private val mutableStatus = MutableStateFlow(
        NovelImportStatus(
            displayStatus = LightNovelTransferDisplayStatus.COMPLETED,
            progressPercent = 100,
        ),
    )
    val status = mutableStatus.asStateFlow()

    fun onPreparing() {
        latestProgressAtMs = 0L
        mutableStatus.value = NovelImportStatus(
            displayStatus = LightNovelTransferDisplayStatus.PREPARING,
            progressPercent = 0,
        )
    }

    fun onImportProgress(bytesRead: Long, contentLength: Long?) {
        val now = nowMs()
        latestProgressAtMs = now
        val current = mutableStatus.value
        val percent = contentLength
            ?.takeIf { it > 0L }
            ?.let { ((bytesRead.coerceAtLeast(0L) * 100L) / it).toInt().coerceIn(0, 100) }
        if (!shouldEmitProgressUpdate(current, percent, now)) return
        mutableStatus.value = current.copy(
            displayStatus = LightNovelTransferDisplayStatus.IMPORTING,
            lastProgressAt = now,
            progressPercent = percent,
            lastErrorReason = null,
        )
    }

    fun onVerifying() {
        val current = mutableStatus.value
        mutableStatus.value = current.copy(
            displayStatus = LightNovelTransferDisplayStatus.VERIFYING,
        )
    }

    fun onCompleted() {
        latestProgressAtMs = 0L
        mutableStatus.value = NovelImportStatus(
            displayStatus = LightNovelTransferDisplayStatus.COMPLETED,
            progressPercent = 100,
        )
    }

    fun onFailed(reason: String) {
        val current = mutableStatus.value
        mutableStatus.value = current.copy(
            displayStatus = LightNovelTransferDisplayStatus.FAILED,
            lastErrorReason = reason,
        )
    }

    fun onPausedLowStorage(reason: String) {
        val current = mutableStatus.value
        mutableStatus.value = current.copy(
            displayStatus = LightNovelTransferDisplayStatus.PAUSED_LOW_STORAGE,
            lastErrorReason = reason,
        )
    }

    fun updateStalledIfNeeded() {
        val current = mutableStatus.value
        val stalledSince = latestProgressAtMs.takeIf { it > 0L } ?: current.lastProgressAt
        if (shouldMarkStalled(current, stalledSince, nowMs())) {
            mutableStatus.value = current.copy(
                displayStatus = LightNovelTransferDisplayStatus.STALLED,
            )
        }
    }

    companion object {
        const val STALL_THRESHOLD_MS = 10_000L
        private const val UNKNOWN_SIZE_EMIT_INTERVAL_MS = 300L

        fun shouldMarkStalled(snapshot: LightNovelTransferSnapshot, nowMs: Long): Boolean {
            return shouldMarkStalled(snapshot, snapshot.lastProgressAt, nowMs)
        }

        private fun shouldMarkStalled(
            snapshot: LightNovelTransferSnapshot,
            lastProgressAt: Long,
            nowMs: Long,
        ): Boolean {
            if (snapshot.displayStatus != LightNovelTransferDisplayStatus.IMPORTING) return false
            if (lastProgressAt <= 0L) return false
            return nowMs - lastProgressAt >= STALL_THRESHOLD_MS
        }

        private fun shouldEmitProgressUpdate(
            current: NovelImportStatus,
            nextPercent: Int?,
            nowMs: Long,
        ): Boolean {
            if (current.displayStatus != LightNovelTransferDisplayStatus.IMPORTING) return true

            if (nextPercent != null) {
                return nextPercent != current.progressPercent
            }

            val elapsed = nowMs - current.lastProgressAt
            return current.progressPercent != null || elapsed >= UNKNOWN_SIZE_EMIT_INTERVAL_MS
        }
    }
}
