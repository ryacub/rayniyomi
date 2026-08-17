package tachiyomi.domain.source.manga.service

import eu.kanade.tachiyomi.source.MangaSource
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import tachiyomi.domain.source.manga.model.StubMangaSource

interface MangaSourceManager {

    val isInitialized: StateFlow<Boolean>

    val sources: Flow<List<MangaSource>>

    fun get(sourceKey: Long): MangaSource?

    fun getOrStub(sourceKey: Long): MangaSource

    fun getAll(): List<MangaSource>

    fun getOnlineSources(): List<HttpSource>

    fun getStubSources(): List<StubMangaSource>
}
