package eu.kanade.tachiyomi.ui.entries.common

import kotlinx.coroutines.flow.Flow

class EntryDownloadProgress(
    val flow: Flow<Int>,
    private val currentProgress: () -> Int,
) {
    val value: Int
        get() = currentProgress()

    override fun equals(other: Any?): Boolean = other is EntryDownloadProgress && flow === other.flow

    override fun hashCode(): Int = System.identityHashCode(flow)
}
