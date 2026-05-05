package eu.kanade.tachiyomi.data.backup.restore.restorers

import androidx.paging.PagingSource
import app.cash.sqldelight.ExecutableQuery
import app.cash.sqldelight.Query
import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.manga.interactor.GetMangaExtensionRepo
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import mihon.domain.extensionrepo.model.ExtensionRepo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.data.Database
import tachiyomi.data.handlers.manga.MangaDatabaseHandler

class MangaExtensionRepoRestorerTest {

    @Test
    fun `already existing repo returns before database insert`() = runTest {
        val existingRepo = createExtensionRepo("https://same.url", "same-fingerprint")
        val backupRepo = createBackupRepo("https://same.url", "same-fingerprint")
        val handler = RecordingMangaDatabaseHandler()
        val restorer = MangaExtensionRepoRestorer(
            mangaHandler = handler,
            getExtensionRepos = GetMangaExtensionRepo(FakeMangaExtensionRepoRepository(listOf(existingRepo))),
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

    private class FakeMangaExtensionRepoRepository(
        private val repos: List<ExtensionRepo>,
    ) : MangaExtensionRepoRepository {
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

    private class RecordingMangaDatabaseHandler : MangaDatabaseHandler {
        var awaitCalls = 0
            private set

        override suspend fun <T> await(inTransaction: Boolean, block: suspend Database.() -> T): T {
            awaitCalls++
            error("Duplicate repo restore should not execute a database write")
        }

        override suspend fun <T : Any> awaitList(
            inTransaction: Boolean,
            block: suspend Database.() -> Query<T>,
        ): List<T> = notImplemented()

        override suspend fun <T : Any> awaitOne(
            inTransaction: Boolean,
            block: suspend Database.() -> Query<T>,
        ): T = notImplemented()

        override suspend fun <T : Any> awaitOneExecutable(
            inTransaction: Boolean,
            block: suspend Database.() -> ExecutableQuery<T>,
        ): T = notImplemented()

        override suspend fun <T : Any> awaitOneOrNull(
            inTransaction: Boolean,
            block: suspend Database.() -> Query<T>,
        ): T? = notImplemented()

        override suspend fun <T : Any> awaitOneOrNullExecutable(
            inTransaction: Boolean,
            block: suspend Database.() -> ExecutableQuery<T>,
        ): T? = notImplemented()

        override fun <T : Any> subscribeToList(block: Database.() -> Query<T>): Flow<List<T>> = notImplemented()

        override fun <T : Any> subscribeToOne(block: Database.() -> Query<T>): Flow<T> = notImplemented()

        override fun <T : Any> subscribeToOneOrNull(block: Database.() -> Query<T>): Flow<T?> = notImplemented()

        override fun <T : Any> subscribeToPagingSource(
            countQuery: Database.() -> Query<Long>,
            queryProvider: Database.(Long, Long) -> Query<T>,
        ): PagingSource<Long, T> = notImplemented()
    }
}

private fun notImplemented(): Nothing = error("Not needed for this test")
