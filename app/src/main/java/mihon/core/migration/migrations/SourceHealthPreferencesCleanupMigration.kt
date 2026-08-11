package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.withIOContext

class SourceHealthPreferencesCleanupMigration : Migration {
    // Migration versions are app version codes, matched against (previousCode + 1)..currentCode.
    // 352 is the code this ships in: main is at 351 and the release bump lands with the merge.
    override val version: Float = 352f

    // R801 removed source health tracking. These keys outlive their accessors and would
    // otherwise keep riding along in backups, which copy every non-app-state preference.
    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return@withIOContext false

        listOf("show_broken_manga_sources", "show_broken_anime_sources")
            .forEach { preferenceStore.getBoolean(it).delete() }

        return@withIOContext true
    }
}
