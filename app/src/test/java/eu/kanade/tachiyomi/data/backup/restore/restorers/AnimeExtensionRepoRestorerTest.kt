package eu.kanade.tachiyomi.data.backup.restore.restorers

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.anime.interactor.GetAnimeExtensionRepo
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.handlers.anime.AnimeDatabaseHandler
import tachiyomi.mi.data.AnimeDatabase

class AnimeExtensionRepoRestorerTest {

    @Test
    fun `already existing repo returns before database insert`() = runTest {
        val existingRepo = createExtensionRepo("https://same.url", "same-fingerprint")
        val backupRepo = createBackupRepo("https://same.url", "same-fingerprint")
        val handler = RecordingAnimeDatabaseHandler()
        val restorer = AnimeExtensionRepoRestorer(
            animeHandler = handler,
            getExtensionRepos = GetAnimeExtensionRepo(FakeAnimeExtensionRepoRepository(listOf(existingRepo))),
        )

        restorer(backupRepo)

        assertEquals(0, handler.awaitCalls)
    }

    private fun createBackupRepo(baseUrl: String, fingerprint: String) = BackupExtensionRepos(
        baseUrl = baseUrl,
        name = "Test Repo",
        shortName = "TR",
        website = "https://test.com",
        signingKeyFingerprint = fingerprint,
    )

    private fun createExtensionRepo(baseUrl: String, fingerprint: String) = ExtensionRepo(
        baseUrl = baseUrl,
        name = "Test Repo",
        shortName = "TR",
        website = "https://test.com",
        signingKeyFingerprint = fingerprint,
    )

    private class FakeAnimeExtensionRepoRepository(
        private val repos: List<ExtensionRepo>,
    ) : AnimeExtensionRepoRepository {
        override fun subscribeAll(): Flow<List<ExtensionRepo>> = notImplemented()

        override suspend fun getAll(): List<ExtensionRepo> = repos

        override suspend fun getRepo(baseUrl: String): ExtensionRepo? = repos.find { it.baseUrl == baseUrl }

        override suspend fun getRepoBySigningKeyFingerprint(fingerprint: String): ExtensionRepo? =
            repos.find { it.signingKeyFingerprint == fingerprint }

        override fun getCount(): Flow<Int> = notImplemented()

        override suspend fun insertRepo(
            baseUrl: String,
            name: String,
            shortName: String?,
            website: String,
            signingKeyFingerprint: String,
        ) = notImplemented()

        override suspend fun upsertRepo(
            baseUrl: String,
            name: String,
            shortName: String?,
            website: String,
            signingKeyFingerprint: String,
        ) = notImplemented()

        override suspend fun replaceRepo(newRepo: ExtensionRepo) = notImplemented()

        override suspend fun deleteRepo(baseUrl: String) = notImplemented()
    }

    private class RecordingAnimeDatabaseHandler : AnimeDatabaseHandler {
        var awaitCalls = 0
            private set

        override suspend fun <T> await(inTransaction: Boolean, block: suspend AnimeDatabase.() -> T): T {
            awaitCalls++
            error("Duplicate repo restore should not execute a database write")
        }

        override suspend fun <T : Any> awaitList(
            inTransaction: Boolean,
            block: suspend AnimeDatabase.() -> Query<T>,
        ): List<T> = notImplemented()

        override suspend fun <T : Any> awaitOne(
            inTransaction: Boolean,
            block: suspend AnimeDatabase.() -> Query<T>,
        ): T = notImplemented()

        override suspend fun <T : Any> awaitOneExecutable(
            inTransaction: Boolean,
            block: suspend AnimeDatabase.() -> ExecutableQuery<T>,
        ): T = notImplemented()

        override suspend fun <T : Any> awaitOneOrNull(
            inTransaction: Boolean,
            block: suspend AnimeDatabase.() -> Query<T>,
        ): T? = notImplemented()

        override suspend fun <T : Any> awaitOneOrNullExecutable(
            inTransaction: Boolean,
            block: suspend AnimeDatabase.() -> ExecutableQuery<T>,
        ): T? = notImplemented()

        override fun <T : Any> subscribeToList(block: AnimeDatabase.() -> Query<T>): Flow<List<T>> = notImplemented()

        override fun <T : Any> subscribeToOne(block: AnimeDatabase.() -> Query<T>): Flow<T> = notImplemented()

        override fun <T : Any> subscribeToOneOrNull(block: AnimeDatabase.() -> Query<T>): Flow<T?> = notImplemented()

        override fun <T : Any> subscribeToPagingSource(
            countQuery: AnimeDatabase.() -> Query<Long>,
            queryProvider: AnimeDatabase.(Long, Long) -> Query<T>,
        ): PagingSource<Long, T> = notImplemented()
    }
}

private fun notImplemented(): Nothing = error("Not needed for this test")
