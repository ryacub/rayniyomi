package tachiyomi.domain.source.manga.model

enum class SourceHealthStatus {
    UNKNOWN,
    HEALTHY,
    DEGRADED,
    BROKEN,
}

data class SourceHealth(
    val sourceId: Long,
    val status: SourceHealthStatus,
    val lastCheckedAt: Long,
    val failureCount: Int,
    val lastError: String?,
)
