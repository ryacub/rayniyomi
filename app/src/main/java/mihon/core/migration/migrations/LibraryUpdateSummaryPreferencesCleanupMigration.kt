package mihon.core.migration.migrations

import eu.kanade.tachiyomi.data.library.deleteLibraryUpdateSummaries
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.util.lang.withIOContext

class LibraryUpdateSummaryPreferencesCleanupMigration : Migration {
    // Migration versions are app version codes, matched against (previousCode + 1)..currentCode.
    // 566 is the code this ships in: main is at 565 and the release bump lands with the merge.
    override val version: Float = 566f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val preferenceStore = migrationContext.graph?.preferenceStore ?: return@withIOContext false

        preferenceStore.deleteLibraryUpdateSummaries()

        return@withIOContext true
    }
}
