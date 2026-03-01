package eu.kanade.tachiyomi.data.download.model

data class TestStatusSnapshot(
    override val isRunningTransfer: Boolean = false,
    override val displayStatus: DownloadDisplayStatus = DownloadDisplayStatus.PREPARING,
    override val lastProgressAt: Long = 0L,
    override val retryAttempt: Int = 0,
    override val lastErrorReason: String? = null,
) : DownloadStatusSnapshot
