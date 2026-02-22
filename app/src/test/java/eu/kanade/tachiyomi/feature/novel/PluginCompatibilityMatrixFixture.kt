package eu.kanade.tachiyomi.feature.novel

import eu.kanade.domain.novel.ReleaseChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal data class PluginCompatibilityMatrixCase(
    val id: String,
    val pluginApiVersion: Int,
    val expectedPluginApiVersion: Int,
    val minHostVersion: Long,
    val targetHostVersion: Long?,
    val hostVersionCode: Long,
    val pluginVersionCode: Long,
    val minPluginVersionCode: Long,
    val pluginChannelRaw: String,
    val hostChannelRaw: String,
    val expectedNormalizedPluginChannel: ReleaseChannel,
    val expectedCompatibility: LightNovelPluginCompatibilityResult,
    val expectedPolicyAllowed: Boolean,
    val expectedPolicyBlockReason: PolicyBlockReason?,
)

@Serializable
private data class RawPluginCompatibilityMatrixCase(
    val id: String,
    val pluginApiVersion: Int,
    val expectedPluginApiVersion: Int,
    val minHostVersion: Long,
    val targetHostVersion: Long? = null,
    val hostVersionCode: Long,
    val pluginVersionCode: Long,
    val minPluginVersionCode: Long,
    val pluginChannelRaw: String,
    val hostChannelRaw: String,
    val expectedNormalizedPluginChannel: String,
    val expectedCompatibility: String,
    val expectedPolicyAllowed: Boolean,
    val expectedPolicyBlockReason: String? = null,
)

internal object PluginCompatibilityMatrixFixture {
    private val json = Json {
        ignoreUnknownKeys = false
    }

    fun loadCases(): List<PluginCompatibilityMatrixCase> {
        val classLoader = requireNotNull(javaClass.classLoader) {
            "Missing classloader for compatibility matrix fixture"
        }
        val input = requireNotNull(
            classLoader.getResourceAsStream("novel/plugin_compatibility_matrix.json"),
        ) { "Missing fixture: novel/plugin_compatibility_matrix.json" }

        val rawCases = input.bufferedReader().use { reader ->
            json.decodeFromString<List<RawPluginCompatibilityMatrixCase>>(reader.readText())
        }

        return rawCases.map { raw ->
            PluginCompatibilityMatrixCase(
                id = raw.id,
                pluginApiVersion = raw.pluginApiVersion,
                expectedPluginApiVersion = raw.expectedPluginApiVersion,
                minHostVersion = raw.minHostVersion,
                targetHostVersion = raw.targetHostVersion,
                hostVersionCode = raw.hostVersionCode,
                pluginVersionCode = raw.pluginVersionCode,
                minPluginVersionCode = raw.minPluginVersionCode,
                pluginChannelRaw = raw.pluginChannelRaw,
                hostChannelRaw = raw.hostChannelRaw,
                expectedNormalizedPluginChannel = ReleaseChannel.fromString(raw.expectedNormalizedPluginChannel),
                expectedCompatibility = LightNovelPluginCompatibilityResult.valueOf(raw.expectedCompatibility),
                expectedPolicyAllowed = raw.expectedPolicyAllowed,
                expectedPolicyBlockReason = raw.expectedPolicyBlockReason?.let(PolicyBlockReason::valueOf),
            )
        }
    }
}
