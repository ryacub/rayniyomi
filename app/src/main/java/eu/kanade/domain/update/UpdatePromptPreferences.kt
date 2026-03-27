package eu.kanade.domain.update

import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum

enum class PromptCadence {
    ALWAYS,
    DAILY,
    WEEKLY,
    NEVER,
}

class UpdatePromptPreferences(private val preferenceStore: PreferenceStore) {

    fun promptCadence(): Preference<PromptCadence> {
        return preferenceStore.getEnum(Preference.privateKey("update_prompt_cadence"), PromptCadence.ALWAYS)
    }

    fun skipVersion(): Preference<String> {
        return preferenceStore.getString(Preference.appStateKey("update_skip_version"), "")
    }

    fun lastPromptedAt(): Preference<Long> {
        return preferenceStore.getLong(Preference.appStateKey("update_last_prompted"), 0L)
    }

    fun includePrerelease(): Preference<Boolean> {
        return preferenceStore.getBoolean(Preference.appStateKey("update_include_prerelease"), false)
    }
}
