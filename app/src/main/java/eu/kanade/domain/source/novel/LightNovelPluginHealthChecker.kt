package eu.kanade.domain.source.novel

import eu.kanade.tachiyomi.feature.novel.LightNovelPluginManager
import tachiyomi.domain.source.health.SourceHealthChecker

class LightNovelPluginHealthChecker(
    private val pluginManager: LightNovelPluginManager,
) : SourceHealthChecker {
    override suspend fun probe(sourceId: Long) {
        if (!pluginManager.isPluginReady()) {
            throw IllegalStateException("LN plugin not ready")
        }
    }

    override fun shouldSkip(sourceId: Long): Boolean = false
}
