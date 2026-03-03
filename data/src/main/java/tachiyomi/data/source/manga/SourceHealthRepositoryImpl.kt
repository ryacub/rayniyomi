package tachiyomi.data.source.manga

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.source.manga.model.SourceHealth
import tachiyomi.domain.source.manga.model.SourceHealthStatus
import tachiyomi.domain.source.manga.repository.SourceHealthRepository

class SourceHealthRepositoryImpl(
    private val handler: MangaDatabaseHandler,
) : SourceHealthRepository {

    override fun getAll(): Flow<Map<Long, SourceHealth>> {
        return handler.subscribeToList { source_healthQueries.findAll(::mapSourceHealth) }
            .map { list -> list.associateBy { it.sourceId } }
    }

    override suspend fun get(sourceId: Long): SourceHealth? {
        return handler.awaitOneOrNull {
            source_healthQueries.findBySourceId(sourceId, ::mapSourceHealth)
        }
    }

    override suspend fun upsert(health: SourceHealth) {
        handler.await {
            source_healthQueries.upsert(
                sourceId = health.sourceId,
                status = health.status.name,
                lastCheckedAt = health.lastCheckedAt,
                failureCount = health.failureCount.toLong(),
                lastError = health.lastError,
            )
        }
    }

    override suspend fun deleteForSource(sourceId: Long) {
        handler.await { source_healthQueries.deleteBySourceId(sourceId) }
    }

    private fun mapSourceHealth(
        sourceId: Long,
        status: String,
        lastCheckedAt: Long,
        failureCount: Long,
        lastError: String?,
    ): SourceHealth = SourceHealth(
        sourceId = sourceId,
        status = try {
            SourceHealthStatus.valueOf(status)
        } catch (_: IllegalArgumentException) {
            SourceHealthStatus.UNKNOWN
        },
        lastCheckedAt = lastCheckedAt,
        failureCount = failureCount.toInt(),
        lastError = lastError,
    )
}
