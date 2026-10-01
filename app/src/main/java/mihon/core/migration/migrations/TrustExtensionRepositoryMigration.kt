package mihon.core.migration.migrations

import logcat.LogPriority
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import mihon.domain.extensionrepo.exception.SaveExtensionRepoException
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.core.common.util.system.logcat

class TrustExtensionRepositoryMigration : Migration {
    override val version: Float = 7f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val sourcePreferences = migrationContext.graph?.sourcePreferences ?: return@withIOContext false

        val animeExtensionRepositoryRepository =
            migrationContext.graph.animeExtensionRepoRepository
        for ((index, source) in sourcePreferences.animeExtensionRepos().get().withIndex()) {
            try {
                animeExtensionRepositoryRepository.upsertRepo(
                    source,
                    "Repo #${index + 1}",
                    null,
                    source,
                    "NOFINGERPRINT-${index + 1}",
                )
            } catch (e: SaveExtensionRepoException) {
                logcat(LogPriority.ERROR, e) { "Error Migrating Extension Repo with baseUrl: $source" }
            }
        }
        sourcePreferences.animeExtensionRepos().delete()

        val mangaExtensionRepositoryRepository =
            migrationContext.graph.mangaExtensionRepoRepository
        for ((index, source) in sourcePreferences.mangaExtensionRepos().get().withIndex()) {
            try {
                mangaExtensionRepositoryRepository.upsertRepo(
                    source,
                    "Repo #${index + 1}",
                    null,
                    source,
                    "NOFINGERPRINT-${index + 1}",
                )
            } catch (e: SaveExtensionRepoException) {
                logcat(LogPriority.ERROR, e) {
                    "Error Migrating Manga Extension Repo with baseUrl: $source"
                }
            }
        }
        sourcePreferences.mangaExtensionRepos().delete()

        return@withIOContext true
    }
}
