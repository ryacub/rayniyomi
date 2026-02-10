package mihon.core.migration

import eu.kanade.tachiyomi.util.system.CrashlyticLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import kotlin.time.Duration.Companion.minutes

class MigrationJobFactory(
    private val migrationContext: MigrationContext,
    private val scope: CoroutineScope,
) {

    fun create(migrations: List<Migration>): Deferred<Boolean> = with(scope) {
        return migrations.sortedBy { it.version }
            .fold(CompletableDeferred(true)) { acc: Deferred<Boolean>, migration: Migration ->
                if (!migrationContext.dryrun) {
                    logcat {
                        "Running migration: { name = ${migration::class.simpleName}, version = ${migration.version} }"
                    }
                    async(start = CoroutineStart.UNDISPATCHED) {
                        val prev = acc.await()
                        val migrationSuccess = executeMigrationSafely(migration)
                        migrationSuccess || prev
                    }
                } else {
                    logcat {
                        "(Dry-run) Running migration: { name = ${migration::class.simpleName}, version = ${migration.version} }"
                    }
                    CompletableDeferred(true)
                }
            }
    }

    private suspend fun executeMigrationSafely(migration: Migration): Boolean {
        val migrationName = migration::class.simpleName ?: "UnknownMigration"
        val migrationVersion = migration.version

        return try {
            withTimeout(MIGRATION_TIMEOUT) {
                CrashlyticLogger.setCustomKey("current_migration_name", migrationName)
                CrashlyticLogger.setCustomKey("current_migration_version", migrationVersion.toString())

                val result = migration(migrationContext)

                CrashlyticLogger.setCustomKey("current_migration_name", "none")

                if (!result) {
                    logcat(LogPriority.WARN) {
                        "Migration returned false: { name = $migrationName, version = $migrationVersion }"
                    }
                    CrashlyticLogger.log("Migration returned false: $migrationName (v$migrationVersion)")
                }

                result
            }
        } catch (e: TimeoutCancellationException) {
            logcat(LogPriority.ERROR, e) {
                "Migration timeout: { name = $migrationName, version = $migrationVersion }"
            }
            CrashlyticLogger.logException(
                Exception("Migration timeout: $migrationName (v$migrationVersion)", e),
                "Migration exceeded timeout",
            )
            CrashlyticLogger.setCustomKey("last_failed_migration", migrationName)
            CrashlyticLogger.setCustomKey("current_migration_name", "none")
            false
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Migration failed: { name = $migrationName, version = $migrationVersion }"
            }
            CrashlyticLogger.logException(
                Exception("Migration failed: $migrationName (v$migrationVersion)", e),
                "Migration execution threw exception",
            )
            CrashlyticLogger.setCustomKey("last_failed_migration", migrationName)
            CrashlyticLogger.setCustomKey("current_migration_name", "none")
            false
        }
    }

    companion object {
        private val MIGRATION_TIMEOUT = 2.minutes
    }
}
