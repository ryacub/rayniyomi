package eu.kanade.tachiyomi.ui.player.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class PlayerPreferencesTest {

    @Test
    fun `migration copies legacy value when new prefs unset`() {
        val store = MutablePreferenceStore(
            mapOf(
                LEGACY_AUTO_SKIP_KEY to true,
            ),
        )

        val preferences = PlayerPreferences(store)

        assertTrue(preferences.autoSkipOpening().get())
        assertTrue(preferences.autoSkipEnding().get())
    }

    @Test
    fun `migration does not overwrite set new preference`() {
        val store = MutablePreferenceStore(
            mapOf(
                LEGACY_AUTO_SKIP_KEY to true,
                OPENING_AUTO_SKIP_KEY to false,
            ),
        )

        val preferences = PlayerPreferences(store)

        assertFalse(preferences.autoSkipOpening().get())
        assertTrue(preferences.autoSkipEnding().get())
    }

    @Test
    fun `migration does nothing when legacy key is unset`() {
        val store = MutablePreferenceStore()

        val preferences = PlayerPreferences(store)

        assertFalse(preferences.autoSkipOpening().isSet())
        assertFalse(preferences.autoSkipEnding().isSet())
    }

    private class MutablePreferenceStore(initialValues: Map<String, Any?> = emptyMap()) : PreferenceStore {
        private val data = initialValues.toMutableMap()

        override fun getString(key: String, defaultValue: String): Preference<String> {
            return MutablePreference(key, defaultValue)
        }

        override fun getLong(key: String, defaultValue: Long): Preference<Long> {
            return MutablePreference(key, defaultValue)
        }

        override fun getInt(key: String, defaultValue: Int): Preference<Int> {
            return MutablePreference(key, defaultValue)
        }

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
            return MutablePreference(key, defaultValue)
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
            return MutablePreference(key, defaultValue)
        }

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
            return MutablePreference(key, defaultValue)
        }

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> {
            return MutablePreference(key, defaultValue)
        }

        override fun getAll(): Map<String, *> {
            return data
        }

        private inner class MutablePreference<T>(
            private val key: String,
            private val defaultValue: T,
        ) : Preference<T> {
            private val state = MutableStateFlow(get())

            override fun key(): String = key

            override fun get(): T {
                @Suppress("UNCHECKED_CAST")
                return (data[key] as T?) ?: defaultValue
            }

            override fun set(value: T) {
                data[key] = value
                state.value = value
            }

            override fun isSet(): Boolean = data.containsKey(key)

            override fun delete() {
                data.remove(key)
                state.value = defaultValue
            }

            override fun defaultValue(): T = defaultValue

            override fun changes(): Flow<T> = state.asStateFlow()

            override fun stateIn(scope: CoroutineScope): StateFlow<T> {
                return state.asStateFlow()
            }
        }
    }

    private companion object {
        const val LEGACY_AUTO_SKIP_KEY = "pref_enable_auto_skip_ani_skip"
        const val OPENING_AUTO_SKIP_KEY = "pref_enable_auto_skip_opening_ani_skip"
    }
}
