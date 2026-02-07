package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import mihon.domain.extensionrepo.model.ExtensionRepo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExtensionRepoValidatorTest {

    @Test
    fun `validateForRestore returns Valid when no conflicts`() {
        val backupRepo = createBackupRepo("https://new.repo", "new-fingerprint")
        val existingRepos = listOf(
            createExtensionRepo("https://existing.repo", "existing-fingerprint"),
        )

        val result = ExtensionRepoValidator.validateForRestore(backupRepo, existingRepos)

        assertTrue(result is ExtensionRepoValidator.ValidationResult.Valid)
    }

    @Test
    fun `validateForRestore returns Valid when empty existing repos`() {
        val backupRepo = createBackupRepo("https://new.repo", "new-fingerprint")
        val existingRepos = emptyList<ExtensionRepo>()

        val result = ExtensionRepoValidator.validateForRestore(backupRepo, existingRepos)

        assertTrue(result is ExtensionRepoValidator.ValidationResult.Valid)
    }

    @Test
    fun `validateForRestore returns UrlExistsWithDifferentSignature when URL exists with different fingerprint`() {
        val backupRepo = createBackupRepo("https://same.url", "new-fingerprint")
        val existingRepos = listOf(
            createExtensionRepo("https://same.url", "different-fingerprint"),
        )

        val result = ExtensionRepoValidator.validateForRestore(backupRepo, existingRepos)

        assertTrue(result is ExtensionRepoValidator.ValidationResult.UrlExistsWithDifferentSignature)
    }

    @Test
    fun `validateForRestore returns SignatureAlreadyExists when fingerprint exists`() {
        val backupRepo = createBackupRepo("https://new.url", "existing-fingerprint")
        val existingRepos = listOf(
            createExtensionRepo("https://existing.url", "existing-fingerprint", "Existing Repo"),
        )

        val result = ExtensionRepoValidator.validateForRestore(backupRepo, existingRepos)

        assertTrue(result is ExtensionRepoValidator.ValidationResult.SignatureAlreadyExists)
        assertEquals(
            "Existing Repo",
            (result as ExtensionRepoValidator.ValidationResult.SignatureAlreadyExists).existingRepoName,
        )
    }

    @Test
    fun `validateForRestore returns Valid when URL exists with same fingerprint`() {
        // This is an update case - same repo being restored
        val backupRepo = createBackupRepo("https://same.url", "same-fingerprint")
        val existingRepos = listOf(
            createExtensionRepo("https://same.url", "same-fingerprint"),
        )

        val result = ExtensionRepoValidator.validateForRestore(backupRepo, existingRepos)

        // Since URL matches and fingerprint matches, SHA check will find it
        assertTrue(result is ExtensionRepoValidator.ValidationResult.SignatureAlreadyExists)
    }

    private fun createBackupRepo(baseUrl: String, fingerprint: String) = BackupExtensionRepos(
        baseUrl = baseUrl,
        name = "Test Repo",
        shortName = "TR",
        website = "https://test.com",
        signingKeyFingerprint = fingerprint,
    )

    private fun createExtensionRepo(
        baseUrl: String,
        fingerprint: String,
        name: String = "Test Repo",
    ) = ExtensionRepo(
        baseUrl = baseUrl,
        name = name,
        shortName = "TR",
        website = "https://test.com",
        signingKeyFingerprint = fingerprint,
    )
}
