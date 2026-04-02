package eu.kanade.domain.extension.anime.interactor

import android.content.pm.PackageInfo
import eu.kanade.domain.source.service.SourcePreferences
import mihon.domain.extensionrepo.anime.repository.AnimeExtensionRepoRepository
import tachiyomi.core.common.preference.getAndSet

class TrustAnimeExtension(
    private val animeExtensionRepoRepository: AnimeExtensionRepoRepository,
    private val preferences: SourcePreferences,
) {

    suspend fun isTrusted(pkgInfo: PackageInfo, fingerprints: List<String>): Boolean {
        val trustedFingerprints = animeExtensionRepoRepository.getAll().map { it.signingKeyFingerprint }.toHashSet()
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

    suspend fun isInvalid(pkgName: String, versionCode: Long, signatureHash: String): Boolean {
        return invalidKey(pkgName, versionCode, signatureHash) in preferences.invalidAnimeExtensions().get()
    }

    fun markInvalid(pkgName: String, versionCode: Long, signatureHash: String) {
        preferences.invalidAnimeExtensions().getAndSet { exts ->
            val removed = exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()

            removed.also { it += invalidKey(pkgName, versionCode, signatureHash) }
        }
    }

    fun clearInvalid(pkgName: String) {
        preferences.invalidAnimeExtensions().getAndSet { exts ->
            exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()
        }
    }

    fun revokeAll() {
        preferences.trustedExtensions().delete()
    }

    private fun invalidKey(pkgName: String, versionCode: Long, signatureHash: String): String {
        return "$pkgName:$versionCode:$signatureHash"
    }
}
