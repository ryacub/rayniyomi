package eu.kanade.tachiyomi.feature.novel

import eu.kanade.domain.novel.ReleaseChannel

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
 * Outcome of [evaluateChannelPolicy].
 */
internal enum class ChannelPolicyResult {
    /** The plugin's channel is accepted by the host's channel preference. */
    ALLOWED,

    /**
     * The plugin targets a looser channel (e.g. [ReleaseChannel.BETA]) than
     * the host's configured channel (e.g. [ReleaseChannel.STABLE]).
     * The host should skip this plugin build and wait for a stable release.
     */
    BLOCKED_WRONG_CHANNEL,
}

/**
 * The reason a plugin load was blocked by [PluginUpdatePolicyEvaluator].
 * `null` when [PolicyEvaluationResult.isAllowed] is `true`.
 */
public enum class PolicyBlockReason {
    /** Plugin's version code is below the host's minimum. */
    PLUGIN_TOO_OLD,

    /** Plugin's channel is incompatible with the host's channel preference. */
    WRONG_CHANNEL,
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

/**
 * Checks whether [pluginChannel] is accepted given the host's [hostChannel] preference.
 *
 * Promotion hierarchy: `STABLE ≥ BETA`.
 * A host on [ReleaseChannel.STABLE] only accepts [ReleaseChannel.STABLE] plugins.
 * A host on [ReleaseChannel.BETA] accepts both [ReleaseChannel.BETA] and [ReleaseChannel.STABLE].
 */
internal fun evaluateChannelPolicy(
    pluginChannel: ReleaseChannel,
    hostChannel: ReleaseChannel,
): ChannelPolicyResult {
    val isAccepted = when (hostChannel) {
        ReleaseChannel.STABLE -> pluginChannel == ReleaseChannel.STABLE
        ReleaseChannel.BETA -> true // beta hosts accept stable and beta
    }
    return if (isAccepted) ChannelPolicyResult.ALLOWED else ChannelPolicyResult.BLOCKED_WRONG_CHANNEL
}

// ── Composite evaluator ───────────────────────────────────────────────────────

/**
 * Evaluates the full plugin update policy by composing version and channel checks.
 *
 * Checks are applied in priority order:
 * 1. Version gate (blocks known-bad / too-old plugins first)
 * 2. Channel gate (blocks beta plugins on stable-channel hosts)
 *
 * @param hostChannel the [ReleaseChannel] the user has configured.
 * @param minPluginVersionCode lowest plugin version code the host will accept;
 *   use `0` to disable the lower bound.
 */
public class PluginUpdatePolicyEvaluator(
    private val hostChannel: ReleaseChannel,
    private val minPluginVersionCode: Long,
) {
    /**
     * Evaluates a candidate plugin against all active policies.
     *
     * @param pluginVersionCode the candidate plugin's version code.
     * @param pluginChannel the [ReleaseChannel] the plugin was built for.
     */
    public fun evaluate(
        pluginVersionCode: Long,
        pluginChannel: ReleaseChannel,
    ): PolicyEvaluationResult {
        val versionResult = evaluateVersionPolicy(pluginVersionCode, minPluginVersionCode)
        if (versionResult == VersionPolicyResult.PLUGIN_TOO_OLD) {
            return PolicyEvaluationResult.blocked(PolicyBlockReason.PLUGIN_TOO_OLD)
        }

        val channelResult = evaluateChannelPolicy(pluginChannel, hostChannel)
        if (channelResult == ChannelPolicyResult.BLOCKED_WRONG_CHANNEL) {
            return PolicyEvaluationResult.blocked(PolicyBlockReason.WRONG_CHANNEL)
        }

        return PolicyEvaluationResult.ALLOWED
    }
}
