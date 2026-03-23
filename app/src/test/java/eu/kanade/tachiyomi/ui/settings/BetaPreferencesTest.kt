package eu.kanade.tachiyomi.ui.settings

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

class BetaPreferencesTest {

    @Test
    fun `all beta feature keys are beta_ prefixed`() {
        val allKeysArePrefixed = BetaFeature.entries.all { it.preferenceKey.startsWith("beta_") }

        assertTrue(allKeysArePrefixed)
    }

    @Test
    fun `enableExperimentalComposeSettings returns false by default when preference not set`() {
        val store = MutablePreferenceStore()

        val preferences = BetaPreferences(store)

        assertFalse(preferences.enableExperimentalComposeSettings().get())
    }

    @Test
    fun `enableExperimentalComposeSettings persists true value`() {
        val store = MutablePreferenceStore()
        val preferences = BetaPreferences(store)

        preferences.enableExperimentalComposeSettings().set(true)

        assertTrue(preferences.enableExperimentalComposeSettings().get())
    }

    @Test
    fun `enableExperimentalComposeSettings persists false value after being set to true`() {
        val store = MutablePreferenceStore()
        val preferences = BetaPreferences(store)

        preferences.enableExperimentalComposeSettings().set(true)
        preferences.enableExperimentalComposeSettings().set(false)

        assertFalse(preferences.enableExperimentalComposeSettings().get())
    }

    @Test
    fun `enableExperimentalComposeSettings uses beta_ prefixed key`() {
        val store = MutablePreferenceStore()
        val preferences = BetaPreferences(store)

        preferences.enableExperimentalComposeSettings().set(true)

        val allPreferences = store.getAll()
        assertTrue(allPreferences.containsKey("beta_enable_experimental_compose_settings"))
    }

    @Test
    fun `enableExperimentalThemingSettings returns false by default when preference not set`() {
        val store = MutablePreferenceStore()

        val preferences = BetaPreferences(store)

        assertFalse(preferences.enableExperimentalThemingSettings().get())
    }

    @Test
    fun `enableExperimentalThemingSettings persists true value`() {
        val store = MutablePreferenceStore()
        val preferences = BetaPreferences(store)

        preferences.enableExperimentalThemingSettings().set(true)

        assertTrue(preferences.enableExperimentalThemingSettings().get())
    }

    @Test
    fun `enableExperimentalThemingSettings uses beta_ prefixed key`() {
        val store = MutablePreferenceStore()
        val preferences = BetaPreferences(store)

        preferences.enableExperimentalThemingSettings().set(true)

        val allPreferences = store.getAll()
        assertTrue(allPreferences.containsKey("beta_enable_experimental_theming_settings"))
    }

    @Test
    fun `feature accessor persists value for compose feature`() {
        val store = MutablePreferenceStore()
        val preferences = BetaPreferences(store)

        preferences.feature(BetaFeature.EXPERIMENTAL_COMPOSE_SETTINGS).set(true)

        assertTrue(preferences.feature(BetaFeature.EXPERIMENTAL_COMPOSE_SETTINGS).get())
    }

    @Test
    fun `feature accessor uses feature key`() {
        val store = MutablePreferenceStore()
        val preferences = BetaPreferences(store)

        preferences.feature(BetaFeature.EXPERIMENTAL_THEMING_SETTINGS).set(true)

        val allPreferences = store.getAll()
        assertTrue(allPreferences.containsKey(BetaFeature.EXPERIMENTAL_THEMING_SETTINGS.preferenceKey))
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
}
