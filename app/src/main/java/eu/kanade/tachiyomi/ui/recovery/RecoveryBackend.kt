package eu.kanade.tachiyomi.ui.recovery

import tachiyomi.domain.recovery.RecoveryPreparer
import tachiyomi.domain.recovery.RecoverySourceSearch
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryDestination
import tachiyomi.domain.recovery.model.RecoveryItemPair
import tachiyomi.domain.recovery.repository.RecoveryApplier
import tachiyomi.domain.recovery.repository.RecoveryRepository

enum class RecoveryMedia { MANGA, ANIME }

data class RecoveryOriginal(
    val id: Long,
    val title: String,
    val thumbnailUrl: String?,
    val sourceId: Long,
    val seasons: Boolean,
)

data class RecoverySourceOption(
    val id: Long,
    val name: String,
    val lang: String,
)

data class RecoveryPreview(
    val destination: RecoveryDestination,
    val pairs: List<RecoveryItemPair>,
    val originalItemCount: Int,
)

interface RecoveryBackend {
    val repository: RecoveryRepository
    val applier: RecoveryApplier
    val preparer: RecoveryPreparer
    val search: RecoverySourceSearch

    suspend fun originals(sourceId: Long?, originalIds: List<Long>): List<RecoveryOriginal>

    suspend fun destinationSources(excludeSourceIds: Set<Long>): List<RecoverySourceOption>

    suspend fun preview(originalId: Long, candidate: RecoveryCandidate): RecoveryPreview

    suspend fun copyCover(originalId: Long, destinationId: Long)
}
