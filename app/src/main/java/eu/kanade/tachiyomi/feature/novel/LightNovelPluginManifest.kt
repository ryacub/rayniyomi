package eu.kanade.tachiyomi.feature.novel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LightNovelPluginManifest(
    @SerialName("package_name")
    val packageName: String,
    @SerialName("version_code")
    val versionCode: Long,
    @SerialName("plugin_api_version")
    val pluginApiVersion: Int,
    @SerialName("min_host_version")
    val minHostVersion: Long,
    @SerialName("target_host_version")
    val targetHostVersion: Long? = null,
    @SerialName("apk_url")
    val apkUrl: String,
    @SerialName("apk_sha256")
    val apkSha256: String,
)
