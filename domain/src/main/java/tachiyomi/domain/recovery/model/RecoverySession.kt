package tachiyomi.domain.recovery.model

data class RecoverySession(
    val id: Long,
    val createdAt: Long,
    val destinationSourceIds: List<Long>,
    val keepOriginal: Boolean,
)
