package tachiyomi.domain.recovery.model

import kotlinx.serialization.Serializable

data class RecoveryApplyRequest(
    val entryId: Long,
    val runToken: String,
    val originalId: Long,
    val destination: RecoveryDestination,
    val pairs: List<RecoveryItemPair>,
    val tracks: List<RecoveryTrack>,
    val keepOriginal: Boolean,
    val now: Long,
)

data class RecoveryDestination(
    val sourceId: Long,
    val url: String,
    val title: String,
    val items: List<RecoveryItem>,
    val seasons: Boolean = false,
    val artist: String? = null,
    val author: String? = null,
    val description: String? = null,
    val genre: List<String>? = null,
    val status: Long = 0,
    val thumbnailUrl: String? = null,
)

data class RecoveryItem(
    val url: String,
    val name: String,
    val number: Double,
    val scanlator: String? = null,
    val dateUpload: Long = 0,
    val sourceOrder: Long = 0,
)

@Serializable
data class RecoveryItemPair(
    val originalUrl: String,
    val destinationUrl: String,
)

data class RecoveryTrack(
    val syncId: Long,
    val remoteId: Long,
    val libraryId: Long?,
    val title: String,
    val lastProgress: Double,
    val totalItems: Long,
    val status: Long,
    val score: Double,
    val remoteUrl: String,
    val startDate: Long,
    val finishDate: Long,
    val private: Boolean,
)

enum class RecoveryReviewReason {
    SELF_TARGET,
    SHARED_DESTINATION,
    DESTINATION_IS_ORIGINAL,
    TRACKER_CONFLICT,
    MAPPING_CHANGED,
    SEASONS_CANDIDATE,
}

sealed interface RecoveryApplyResult {
    data class Completed(val destinationId: Long) : RecoveryApplyResult
    data class NeedsReview(val reason: RecoveryReviewReason) : RecoveryApplyResult
    data class Failed(val message: String) : RecoveryApplyResult
    data object Unsupported : RecoveryApplyResult
    data object NotOwned : RecoveryApplyResult
}
