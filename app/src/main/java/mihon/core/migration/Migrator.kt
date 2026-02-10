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
import kotlin.coroutines.cancellation.CancellationException

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
        } catch (e: CancellationException) {
            throw e
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
        await().also { release() }
    }
}
