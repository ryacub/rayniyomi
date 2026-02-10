package mihon.core.migration

import eu.kanade.tachiyomi.util.system.CrashlyticLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat

object Migrator {

    private var result: Deferred<Boolean>? = null
    val scope = CoroutineScope(Dispatchers.IO + Job())

    fun initialize(
        old: Int,
        new: Int,
        migrations: List<Migration>,
        dryrun: Boolean = false,
        onMigrationComplete: () -> Unit,
    ) {
        val migrationContext = MigrationContext(dryrun)
        val migrationJobFactory = MigrationJobFactory(migrationContext, scope)
        val migrationStrategyFactory = MigrationStrategyFactory(migrationJobFactory, onMigrationComplete)
        val strategy = migrationStrategyFactory.create(old, new)
        result = strategy(migrations)
    }

    suspend fun await(): Boolean {
        val result = result ?: CompletableDeferred(false)
        return try {
            result.await()
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Migration framework error: ${e.message}"
            }
            CrashlyticLogger.logException(
                Exception("Migration framework failure", e),
                "Uncaught exception in Migrator.await()",
            )
            false
        }
    }

    fun release() {
        result = null
    }

    fun awaitAndRelease(): Boolean = runBlocking {
        try {
            await().also { release() }
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) {
                "Critical migration error in awaitAndRelease: ${e.message}"
            }
            CrashlyticLogger.logException(
                Exception("Critical migration error in awaitAndRelease", e),
                "Uncaught exception in Migrator.awaitAndRelease()",
            )
            release()
            false
        }
    }
}
