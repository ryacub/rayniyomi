package eu.kanade.domain.extension.manga.interactor

import android.content.pm.PackageInfo
import eu.kanade.domain.source.service.SourcePreferences
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import tachiyomi.core.common.preference.getAndSet

class TrustMangaExtension(
    private val mangaExtensionRepoRepository: MangaExtensionRepoRepository,
    private val preferences: SourcePreferences,
) {

    suspend fun isTrusted(pkgInfo: PackageInfo, fingerprints: List<String>): Boolean {
        val trustedFingerprints = mangaExtensionRepoRepository.getAll().map { it.signingKeyFingerprint }.toHashSet()
        return trustedFingerprints.any { fingerprints.contains(it) } ||
            preferences.trustedExtensions().get().any { key ->
                val parts = key.split(":")
                parts.size == 3 && parts[0] == pkgInfo.packageName && fingerprints.contains(parts[2])
            }
    }

    fun trust(pkgName: String, versionCode: Long, signatureHash: String) {
        preferences.trustedExtensions().getAndSet { exts ->
            // Remove previously trusted versions
            val removed = exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()

            removed.also { it += "$pkgName:$versionCode:$signatureHash" }
        }
    }

    fun revoke(pkgName: String) {
        preferences.trustedExtensions().getAndSet { exts ->
            exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()
        }
    }

    fun revokeAll() {
        preferences.trustedExtensions().delete()
    }
}
