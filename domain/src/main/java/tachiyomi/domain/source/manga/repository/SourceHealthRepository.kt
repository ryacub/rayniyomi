package tachiyomi.domain.source.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.source.manga.model.SourceHealth

interface SourceHealthRepository {

    fun getAll(): Flow<Map<Long, SourceHealth>>

    suspend fun get(sourceId: Long): SourceHealth?

    suspend fun upsert(health: SourceHealth)

    suspend fun deleteForSource(sourceId: Long)
}
