package tachiyomi.domain.library.service

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@Execution(ExecutionMode.CONCURRENT)
class LibraryPreferencesTest {

    @Test
    fun `libraryListSize returns default value of 0 when not set`() {
        val store = MutablePreferenceStore()
        val preferences = LibraryPreferences(store)

        preferences.libraryListSize().get() shouldBe 0
        preferences.libraryListSize().isSet() shouldBe false
    }

    @Test
    fun `libraryListSize persists set value`() {
        val store = MutablePreferenceStore()
        val preferences = LibraryPreferences(store)

        preferences.libraryListSize().set(5)

        preferences.libraryListSize().get() shouldBe 5
        preferences.libraryListSize().isSet() shouldBe true
    }

    @Test
    fun `libraryListSize handles boundary values`() {
        val store = MutablePreferenceStore()
        val preferences = LibraryPreferences(store)

        // Test minimum (auto-size)
        preferences.libraryListSize().set(0)
        preferences.libraryListSize().get() shouldBe 0

        // Test maximum
        preferences.libraryListSize().set(12)
        preferences.libraryListSize().get() shouldBe 12

        // Test mid-range
        preferences.libraryListSize().set(6)
        preferences.libraryListSize().get() shouldBe 6
    }

    @Test
    fun `libraryListSize can be deleted to restore default`() {
        val store = MutablePreferenceStore()
        val preferences = LibraryPreferences(store)

        preferences.libraryListSize().set(8)
        preferences.libraryListSize().isSet() shouldBe true

        preferences.libraryListSize().delete()

        preferences.libraryListSize().get() shouldBe 0
        preferences.libraryListSize().isSet() shouldBe false
    }

    /**
     * Mutable in-memory PreferenceStore implementation for testing.
     * Copied from PlayerPreferencesTest pattern.
     */
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
