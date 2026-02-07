package eu.kanade.tachiyomi.data.backup

import eu.kanade.tachiyomi.data.backup.models.BackupExtensionRepos
import mihon.domain.extensionrepo.model.ExtensionRepo

/**
 * Shared validation logic for extension repo backup/restore operations.
 * Used by both AnimeExtensionRepoRestorer and MangaExtensionRepoRestorer.
 */
object ExtensionRepoValidator {

    /**
     * Validates whether a backup extension repo can be restored.
     *
     * @param backupRepo The extension repo from backup to validate
     * @param existingRepos Current extension repos in the database
     * @return ValidationResult indicating success or specific error
     */
    fun validateForRestore(
        backupRepo: BackupExtensionRepos,
        existingRepos: List<ExtensionRepo>,
    ): ValidationResult {
        val existingReposBySHA = existingRepos.associateBy { it.signingKeyFingerprint }
        val existingReposByUrl = existingRepos.associateBy { it.baseUrl }

        val urlExists = existingReposByUrl[backupRepo.baseUrl]
        val shaExists = existingReposBySHA[backupRepo.signingKeyFingerprint]

        return when {
            urlExists != null && urlExists.signingKeyFingerprint != backupRepo.signingKeyFingerprint -> {
                ValidationResult.UrlExistsWithDifferentSignature
            }
            shaExists != null -> {
                ValidationResult.SignatureAlreadyExists(shaExists.name)
            }
            else -> {
                ValidationResult.Valid
            }
        }
    }

    sealed class ValidationResult {
        data object Valid : ValidationResult()
        data object UrlExistsWithDifferentSignature : ValidationResult()
        data class SignatureAlreadyExists(val existingRepoName: String) : ValidationResult()

        fun throwIfInvalid() {
            when (this) {
                is Valid -> { /* Success */ }
                is UrlExistsWithDifferentSignature -> {
                    error("Already Exists with different signing key fingerprint")
                }
                is SignatureAlreadyExists -> {
                    error("$existingRepoName has the same signing key fingerprint")
                }
            }
        }
    }
}
