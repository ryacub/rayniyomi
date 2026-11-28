package eu.kanade.tachiyomi.extension.util

import android.content.Intent

internal fun parseExtensionInstallIntent(intent: Intent, downloadIdKey: String): ExtensionInstallRequest? {
    val downloadId = intent.getLongExtra(downloadIdKey, -1L)
    if (downloadId == -1L) return null
    val apkUri = intent.data ?: return null
    return ExtensionInstallRequest(downloadId, apkUri)
}
