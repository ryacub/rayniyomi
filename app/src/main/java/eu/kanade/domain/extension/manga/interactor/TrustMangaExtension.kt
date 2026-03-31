package eu.kanade.domain.extension.manga.interactor

import android.content.pm.PackageInfo
import androidx.core.content.pm.PackageInfoCompat
import eu.kanade.domain.source.service.SourcePreferences
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import tachiyomi.core.common.preference.getAndSet

class TrustMangaExtension(
    private val mangaExtensionRepoRepository: MangaExtensionRepoRepository,
    private val preferences: SourcePreferences,
) {

    suspend fun isTrusted(pkgInfo: PackageInfo, fingerprints: List<String>): Boolean {
        val trustedFingerprints = mangaExtensionRepoRepository.getAll().map { it.signingKeyFingerprint }.toHashSet()
        val key = "${pkgInfo.packageName}:${PackageInfoCompat.getLongVersionCode(pkgInfo)}:${fingerprints.last()}"
        return trustedFingerprints.any { fingerprints.contains(it) } || key in preferences.trustedExtensions().get()
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
        return invalidKey(pkgName, versionCode, signatureHash) in preferences.invalidMangaExtensions().get()
    }

    fun markInvalid(pkgName: String, versionCode: Long, signatureHash: String) {
        preferences.invalidMangaExtensions().getAndSet { exts ->
            val removed = exts.filterNot { it.startsWith("$pkgName:") }.toMutableSet()

            removed.also { it += invalidKey(pkgName, versionCode, signatureHash) }
        }
    }

    fun clearInvalid(pkgName: String) {
        preferences.invalidMangaExtensions().getAndSet { exts ->
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
