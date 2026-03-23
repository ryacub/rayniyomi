package eu.kanade.tachiyomi.feature.novel

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

            val policyResult = PluginUpdatePolicyEvaluator(
                minPluginVersionCode = case.minPluginVersionCode,
            ).evaluate(
                pluginVersionCode = case.pluginVersionCode,
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
