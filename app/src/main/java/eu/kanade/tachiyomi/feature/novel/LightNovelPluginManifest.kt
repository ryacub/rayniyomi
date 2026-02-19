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
    /**
     * The minimum plugin version code the host will accept.
     *
     * When the currently installed plugin's version code falls below this value
     * (e.g. after a rollback to a version that was later declared known-bad) the
     * host blocks the plugin and prompts the user to update.
     *
     * A value of `0` means the host imposes no lower bound.
     *
     * Added in R236-J.
     */
    @SerialName("min_plugin_version_code")
    val minPluginVersionCode: Long = 0L,
    /**
     * The release channel this build targets: `"stable"` or `"beta"`.
     *
     * Hosts whose channel preference is [eu.kanade.domain.novel.ReleaseChannel.STABLE]
     * refuse to install beta plugin builds. Defaults to `"stable"` so manifests
     * produced before R236-J are handled gracefully.
     *
     * Added in R236-J.
     */
    @SerialName("release_channel")
    val releaseChannel: String = "stable",
)
