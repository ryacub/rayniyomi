package eu.kanade.tachiyomi.feature.novel

import eu.kanade.domain.novel.ReleaseChannel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PluginCompatibilityMatrixTest {

    @Test
    fun `matrix cases satisfy compatibility and policy contract`() {
        val cases = PluginCompatibilityMatrixFixture.loadCases()
        require(cases.isNotEmpty()) { "Compatibility matrix must not be empty" }

        cases.forEach { case ->
            val normalizedTargetHostVersion = normalizeTargetHostVersion(case.targetHostVersion)
            val compatibility = evaluateLightNovelPluginCompatibility(
                pluginApiVersion = case.pluginApiVersion,
                minHostVersion = case.minHostVersion,
                targetHostVersion = normalizedTargetHostVersion,
                hostVersionCode = case.hostVersionCode,
                expectedPluginApiVersion = case.expectedPluginApiVersion,
            )
            assertEquals(
                case.expectedCompatibility,
                compatibility,
                "Compatibility mismatch for case ${case.id}",
            )

            val pluginChannel = ReleaseChannel.fromString(case.pluginChannelRaw)
            assertEquals(
                case.expectedNormalizedPluginChannel,
                pluginChannel,
                "Channel normalization mismatch for case ${case.id}",
            )

            val policyResult = PluginUpdatePolicyEvaluator(
                hostChannel = ReleaseChannel.fromString(case.hostChannelRaw),
                minPluginVersionCode = case.minPluginVersionCode,
            ).evaluate(
                pluginVersionCode = case.pluginVersionCode,
                pluginChannel = pluginChannel,
            )
            assertEquals(
                case.expectedPolicyAllowed,
                policyResult.isAllowed,
                "Policy allow mismatch for case ${case.id}",
            )
            assertEquals(
                case.expectedPolicyBlockReason,
                policyResult.blockReason,
                "Policy block reason mismatch for case ${case.id}",
            )
        }
    }
}
