package eu.kanade.tachiyomi.feature.novel

// ── Result types ─────────────────────────────────────────────────────────────

/**
 * Outcome of [evaluateVersionPolicy].
 */
internal enum class VersionPolicyResult {
    /** Plugin version satisfies the host's minimum requirement. */
    COMPATIBLE,

    /**
     * Plugin version code is below [minPluginVersionCode].
     * The host should refuse to load the plugin and surface an upgrade prompt.
     */
    PLUGIN_TOO_OLD,
}

/**
 * The reason a plugin load was blocked by [PluginUpdatePolicyEvaluator].
 * `null` when [PolicyEvaluationResult.isAllowed] is `true`.
 */
public enum class PolicyBlockReason {
    /** Plugin's version code is below the host's minimum. */
    PLUGIN_TOO_OLD,
}

/**
 * Combined result returned by [PluginUpdatePolicyEvaluator.evaluate].
 *
 * @property isAllowed `true` if the plugin passes all policy checks.
 * @property blockReason non-null when [isAllowed] is `false`, describes why the plugin was blocked.
 */
public data class PolicyEvaluationResult(
    val isAllowed: Boolean,
    val blockReason: PolicyBlockReason?,
) {
    companion object {
        internal val ALLOWED = PolicyEvaluationResult(isAllowed = true, blockReason = null)

        internal fun blocked(reason: PolicyBlockReason) =
            PolicyEvaluationResult(isAllowed = false, blockReason = reason)
    }
}

// ── Atomic policy functions ───────────────────────────────────────────────────

/**
 * Checks whether [pluginVersionCode] satisfies the host's [minPluginVersionCode] gate.
 *
 * A [minPluginVersionCode] of `0` means the host imposes no lower bound.
 */
internal fun evaluateVersionPolicy(
    pluginVersionCode: Long,
    minPluginVersionCode: Long,
): VersionPolicyResult =
    if (pluginVersionCode >= minPluginVersionCode) {
        VersionPolicyResult.COMPATIBLE
    } else {
        VersionPolicyResult.PLUGIN_TOO_OLD
    }

// ── Composite evaluator ───────────────────────────────────────────────────────

/**
 * Evaluates the full plugin update policy by applying version checks.
 *
 * Checks are applied in priority order:
 * 1. Version gate (blocks known-bad / too-old plugins first)
 *
 * @param minPluginVersionCode lowest plugin version code the host will accept;
 *   use `0` to disable the lower bound.
 */
public class PluginUpdatePolicyEvaluator(
    private val minPluginVersionCode: Long,
) {
    /**
     * Evaluates a candidate plugin against all active policies.
     *
     * @param pluginVersionCode the candidate plugin's version code.
     */
    public fun evaluate(
        pluginVersionCode: Long,
    ): PolicyEvaluationResult {
        val versionResult = evaluateVersionPolicy(pluginVersionCode, minPluginVersionCode)
        if (versionResult == VersionPolicyResult.PLUGIN_TOO_OLD) {
            return PolicyEvaluationResult.blocked(PolicyBlockReason.PLUGIN_TOO_OLD)
        }

        return PolicyEvaluationResult.ALLOWED
    }
}
