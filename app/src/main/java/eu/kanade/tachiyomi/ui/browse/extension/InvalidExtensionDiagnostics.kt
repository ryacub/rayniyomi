package eu.kanade.tachiyomi.ui.browse.extension

internal data class InvalidExtensionDiagnostics(
    val pkgName: String,
    val version: String,
    val signatureHashDisplay: String,
    val debugDetail: String?,
) {
    companion object {
        private const val SIGNATURE_HASH_PREFIX_LENGTH = 12

        fun from(
            pkgName: String,
            versionName: String,
            versionCode: Long,
            signatureHash: String,
            debugDetail: String?,
        ): InvalidExtensionDiagnostics {
            return InvalidExtensionDiagnostics(
                pkgName = pkgName,
                version = "$versionName ($versionCode)",
                signatureHashDisplay = signatureHash.toDisplayHash(),
                debugDetail = debugDetail?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        private fun String.toDisplayHash(): String {
            return if (length > SIGNATURE_HASH_PREFIX_LENGTH) {
                "${take(SIGNATURE_HASH_PREFIX_LENGTH)}..."
            } else {
                this
            }
        }
    }
}
