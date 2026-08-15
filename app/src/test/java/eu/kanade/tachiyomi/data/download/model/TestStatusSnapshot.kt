package eu.kanade.tachiyomi.data.download.model

data class TestStatusSnapshot(
    override val isRunningTransfer: Boolean = false,
    override var displayStatus: DownloadDisplayStatus = DownloadDisplayStatus.PREPARING,
    override var lastProgressAt: Long = 0L,
    override var retryAttempt: Int = 0,
    override val lastErrorReason: String? = null,
) : DownloadStatusSnapshot
