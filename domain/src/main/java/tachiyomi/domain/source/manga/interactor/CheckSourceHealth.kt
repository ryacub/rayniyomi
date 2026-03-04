package tachiyomi.domain.source.manga.interactor

import tachiyomi.domain.source.health.RunSourceHealthCheck
import tachiyomi.domain.source.health.SourceHealthChecker
import tachiyomi.domain.source.manga.model.SourceHealth

class CheckSourceHealth(
    private val runner: RunSourceHealthCheck,
    private val checker: SourceHealthChecker,
) {
    suspend fun check(sourceId: Long): SourceHealth = runner.check(sourceId, checker)
}
