package tachiyomi.domain.category.interactor

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.domain.library.service.LibraryPreferences

class SetCategoryDisplayModeTest {

    @Test
    fun `sets global library display mode preference`() {
        val store = MutablePreferenceStore()
        val preferences = LibraryPreferences(store)
        val setCategoryDisplayMode = SetCategoryDisplayMode(preferences)

        setCategoryDisplayMode.await(LibraryDisplayMode.List)

        preferences.displayMode().get() shouldBe LibraryDisplayMode.List
        store.getAll() shouldBe mapOf("pref_display_mode_library" to "LIST")
    }

    private class MutablePreferenceStore(
        initialValues: Map<String, Any?> = emptyMap(),
    ) : PreferenceStore {

        private val values = initialValues.toMutableMap()

        override fun getString(key: String, defaultValue: String): Preference<String> =
            getPreference(key, defaultValue)

        override fun getLong(key: String, defaultValue: Long): Preference<Long> =
            getPreference(key, defaultValue)

        override fun getInt(key: String, defaultValue: Int): Preference<Int> =
            getPreference(key, defaultValue)

        override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
            getPreference(key, defaultValue)

        override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
            getPreference(key, defaultValue)

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            getPreference(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = ObjectPreference(key, defaultValue, values, serializer, deserializer)

        override fun getAll(): Map<String, *> = values.toMap()

        private fun <T> getPreference(key: String, defaultValue: T): Preference<T> {
            return MutablePreference(key, defaultValue, values)
        }
    }

    private class MutablePreference<T>(
        private val key: String,
        private val defaultValue: T,
        private val values: MutableMap<String, Any?>,
    ) : Preference<T> {

        private val flow = MutableStateFlow(currentValue())

        override fun key(): String = key

        override fun get(): T = currentValue()

        override fun set(value: T) {
            values[key] = value
            flow.value = value
        }

        override fun isSet(): Boolean = values.containsKey(key)

        override fun delete() {
            values.remove(key)
            flow.value = defaultValue
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = flow

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = flow.asStateFlow()

        @Suppress("UNCHECKED_CAST")
        private fun currentValue(): T = values.getOrDefault(key, defaultValue) as T
    }

    private class ObjectPreference<T>(
        private val key: String,
        private val defaultValue: T,
        private val values: MutableMap<String, Any?>,
        private val serializer: (T) -> String,
        private val deserializer: (String) -> T,
    ) : Preference<T> {

        private val flow = MutableStateFlow(currentValue())

        override fun key(): String = key

        override fun get(): T = currentValue()

        override fun set(value: T) {
            values[key] = serializer(value)
            flow.value = value
        }

        override fun isSet(): Boolean = values.containsKey(key)

        override fun delete() {
            values.remove(key)
            flow.value = defaultValue
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = flow

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = flow.asStateFlow()

        private fun currentValue(): T {
            return (values[key] as? String)?.let(deserializer) ?: defaultValue
        }
    }
}
