package tachiyomi.domain.recovery.model

import kotlinx.serialization.Serializable

data class RecoveryEntry(
    val id: Long,
    val sessionId: Long,
    val originalId: Long,
    val selected: Boolean,
    val state: RecoveryState,
    val searchQuery: String,
    val candidates: List<RecoveryCandidate>,
    val searchErrors: Map<Long, String>,
    val choice: RecoveryCandidate?,
    val pairs: List<RecoveryItemPair>,
    val resultId: Long?,
    val error: String?,
    val runToken: String?,
)

data class NewRecoveryEntry(
    val originalId: Long,
    val searchQuery: String,
    val state: RecoveryState,
)

@Serializable
data class RecoveryCandidate(
    val sourceId: Long,
    val url: String,
    val title: String,
)

data class RecoveryIdentity(
    val sourceId: Long,
    val url: String,
)
