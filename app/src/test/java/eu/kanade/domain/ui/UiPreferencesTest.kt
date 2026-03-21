package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.presentation.more.settings.widget.normalizeAccentSeed
import eu.kanade.presentation.more.settings.widget.resolvePickerResultSeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class UiPreferencesTest {

    @Test
    fun `schema seed takes precedence over legacy seed`() {
        val preferences = UiPreferences(
            MutablePreferenceStore(
                initialValues = mapOf(
                    "pref_custom_theme_accent_seed" to 0xFF112233.toInt(),
                    "pref_custom_theme_accent_schema" to "v1:80445566",
                ),
            ),
        )

        assertEquals(0xFF445566.toInt(), preferences.customThemeAccentSeed().get())
    }

    @Test
    fun `invalid schema falls back to unset when legacy missing`() {
        val preferences = UiPreferences(
            MutablePreferenceStore(
                initialValues = mapOf(
                    "pref_custom_theme_accent_schema" to "v999:not-a-seed",
                ),
            ),
        )

        assertEquals(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET, preferences.customThemeAccentSeed().get())
    }

    @Test
    fun `malformed schema falls back to legacy seed`() {
        val preferences = UiPreferences(
            MutablePreferenceStore(
                initialValues = mapOf(
                    "pref_custom_theme_accent_seed" to 0xFF336699.toInt(),
                    "pref_custom_theme_accent_schema" to "broken",
                ),
            ),
        )

        assertEquals(0xFF336699.toInt(), preferences.customThemeAccentSeed().get())
    }

    @Test
    fun `unknown schema version falls back to legacy seed`() {
        val preferences = UiPreferences(
            MutablePreferenceStore(
                initialValues = mapOf(
                    "pref_custom_theme_accent_seed" to 0xFF778899.toInt(),
                    "pref_custom_theme_accent_schema" to "v2:ffaa5500",
                ),
            ),
        )

        assertEquals(0xFF778899.toInt(), preferences.customThemeAccentSeed().get())
    }

    @Test
    fun `setting schema-backed preference keeps legacy key for downgrade safety`() {
        val store = MutablePreferenceStore()
        val preferences = UiPreferences(store)

        preferences.customThemeAccentSeed().set(0x00224466)

        assertEquals(0xFF224466.toInt(), store.getAll()["pref_custom_theme_accent_seed"])
    }

    @Test
    fun `deleting schema-backed preference resets to unset sentinel`() {
        val preferences = UiPreferences(MutablePreferenceStore())

        preferences.customThemeAccentSeed().set(0xFF334455.toInt())
        preferences.customThemeAccentSeed().delete()

        assertEquals(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET, preferences.customThemeAccentSeed().get())
        assertEquals(false, preferences.customThemeAccentSeed().isSet())
    }

    @Test
    fun `active changes collector does not resurrect deleted accent value`() = runTest {
        val store = MutablePreferenceStore()
        val preferences = UiPreferences(store)
        val preference = preferences.customThemeAccentSeed()

        val collector = launch { preference.changes().collect {} }
        preference.set(0xFF445566.toInt())
        preference.delete()
        advanceUntilIdle()
        collector.cancelAndJoin()

        assertEquals(false, preference.isSet())
        assertEquals(null, store.getAll()["pref_custom_theme_accent_seed"])
    }

    @Test
    fun `custom theme accent seed defaults to unset sentinel`() {
        val preferences = UiPreferences(MutablePreferenceStore())

        assertEquals(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET, preferences.customThemeAccentSeed().get())
    }

    @Test
    fun `custom theme accent seed is not marked as set on clean read`() {
        val preferences = UiPreferences(MutablePreferenceStore())

        preferences.customThemeAccentSeed().get()

        assertEquals(false, preferences.customThemeAccentSeed().isSet())
    }

    @Test
    fun `custom theme accent seed persists chosen value`() {
        val preferences = UiPreferences(MutablePreferenceStore())

        preferences.customThemeAccentSeed().set(0xFF4285F4.toInt())

        assertEquals(0xFF4285F4.toInt(), preferences.customThemeAccentSeed().get())
    }

    @Test
    fun `custom accent updates do not change dynamic entry cover theming preference`() {
        val preferences = UiPreferences(MutablePreferenceStore())

        preferences.dynamicEntryCoverTheming().set(true)
        preferences.customThemeAccentSeed().set(0xFF1E88E5.toInt())

        assertEquals(true, preferences.dynamicEntryCoverTheming().get())
    }

    @Test
    fun `custom accent flow supports swatch cancel apply reset and reopen`() {
        val store = MutablePreferenceStore()
        val preferences = UiPreferences(store)
        val appTheme = preferences.appTheme()
        val customAccent = preferences.customThemeAccentSeed()

        appTheme.set(AppTheme.CUSTOM)
        val swatchSelection = normalizeAccentSeed(0x004285F4)
        customAccent.set(swatchSelection)
        assertEquals(0xFF4285F4.toInt(), customAccent.get())

        val cancelledSelection = resolvePickerResultSeed(
            confirmSelection = false,
            currentSeed = customAccent.get(),
            draftSeed = 0x00000000,
        )
        assertEquals(0xFF4285F4.toInt(), cancelledSelection)
        assertEquals(0xFF4285F4.toInt(), customAccent.get())

        val appliedSelection = resolvePickerResultSeed(
            confirmSelection = true,
            currentSeed = customAccent.get(),
            draftSeed = 0x00010203,
        )
        customAccent.set(appliedSelection)
        assertEquals(0xFF010203.toInt(), customAccent.get())

        appTheme.set(AppTheme.DEFAULT)
        appTheme.set(AppTheme.CUSTOM)
        assertEquals(0xFF010203.toInt(), customAccent.get())

        customAccent.set(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET)
        assertEquals(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET, customAccent.get())

        val reopenedPreferences = UiPreferences(store)
        assertEquals(
            UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET,
            reopenedPreferences.customThemeAccentSeed().get(),
        )
    }

    @Test
    fun `custom theme accent seed reset uses unset sentinel`() {
        val preferences = UiPreferences(MutablePreferenceStore())

        preferences.customThemeAccentSeed().set(0xFF4285F4.toInt())
        preferences.customThemeAccentSeed().set(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET)

        assertEquals(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET, preferences.customThemeAccentSeed().get())
    }

    @Test
    fun `recent custom accents normalize dedupe and keep mru order`() {
        val preferences = UiPreferences(MutablePreferenceStore())

        preferences.addCustomThemeRecentAccentSeed(0x00112233)
        preferences.addCustomThemeRecentAccentSeed(0xFF445566.toInt())
        preferences.addCustomThemeRecentAccentSeed(0x00112233)

        assertEquals(
            listOf(0xFF112233.toInt(), 0xFF445566.toInt()),
            preferences.customThemeRecentAccentSeeds().get(),
        )
    }

    @Test
    fun `recent custom accents are limited to five and exclude unset sentinel`() {
        val preferences = UiPreferences(MutablePreferenceStore())

        preferences.addCustomThemeRecentAccentSeed(UiPreferences.CUSTOM_THEME_ACCENT_SEED_UNSET)
        preferences.addCustomThemeRecentAccentSeed(0xFF000001.toInt())
        preferences.addCustomThemeRecentAccentSeed(0xFF000002.toInt())
        preferences.addCustomThemeRecentAccentSeed(0xFF000003.toInt())
        preferences.addCustomThemeRecentAccentSeed(0xFF000004.toInt())
        preferences.addCustomThemeRecentAccentSeed(0xFF000005.toInt())
        preferences.addCustomThemeRecentAccentSeed(0xFF000006.toInt())

        assertEquals(
            listOf(
                0xFF000006.toInt(),
                0xFF000005.toInt(),
                0xFF000004.toInt(),
                0xFF000003.toInt(),
                0xFF000002.toInt(),
            ),
            preferences.customThemeRecentAccentSeeds().get(),
        )
    }

    @Test
    fun `theme mode falls back to default when stored enum value is invalid`() {
        val preferences = UiPreferences(
            MutablePreferenceStore(
                initialValues = mapOf(
                    "pref_theme_mode_key" to "NOT_A_REAL_THEME_MODE",
                ),
            ),
        )

        assertEquals(ThemeMode.SYSTEM, preferences.themeMode().get())
    }

    private class MutablePreferenceStore(initialValues: Map<String, Any?> = emptyMap()) : PreferenceStore {
        private val data = initialValues.toMutableMap()

        override fun getString(key: String, defaultValue: String): Preference<String> = MutablePreference(
            key,
            defaultValue,
        )

        override fun getLong(key: String, defaultValue: Long): Preference<Long> = MutablePreference(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> = MutablePreference(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> = MutablePreference(
            key,
            defaultValue,
        )

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> = MutablePreference(
            key,
            defaultValue,
        )

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> = MutablePreference(
            key,
            defaultValue,
        )

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = SerializedObjectPreference(
            key = key,
            defaultValue = defaultValue,
            serializer = serializer,
            deserializer = deserializer,
        )

        override fun getAll(): Map<String, *> = data

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

            override fun stateIn(scope: CoroutineScope): StateFlow<T> = state.asStateFlow()
        }

        private inner class SerializedObjectPreference<T>(
            private val key: String,
            private val defaultValue: T,
            private val serializer: (T) -> String,
            private val deserializer: (String) -> T,
        ) : Preference<T> {
            private val state = MutableStateFlow(get())

            override fun key(): String = key

            override fun get(): T {
                val raw = data[key] as? String ?: return defaultValue
                return deserializer(raw)
            }

            override fun set(value: T) {
                data[key] = serializer(value)
                state.value = value
            }

            override fun isSet(): Boolean = data.containsKey(key)

            override fun delete() {
                data.remove(key)
                state.value = defaultValue
            }

            override fun defaultValue(): T = defaultValue

            override fun changes(): Flow<T> = state.asStateFlow()

            override fun stateIn(scope: CoroutineScope): StateFlow<T> = state.asStateFlow()
        }
    }
}
