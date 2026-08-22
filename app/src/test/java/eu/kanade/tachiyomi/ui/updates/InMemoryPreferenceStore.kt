package eu.kanade.tachiyomi.ui.updates

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

internal class InMemoryPreferenceStore(
    initialValues: Map<String, Any?> = emptyMap(),
) : PreferenceStore {

    private val values = initialValues.toMutableMap()
    private val preferences = mutableMapOf<String, Preference<*>>()

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
    ): Preference<T> = getPreference(key, defaultValue)

    override fun getAll(): Map<String, *> = values.toMap()

    @Suppress("UNCHECKED_CAST")
    private fun <T> getPreference(key: String, defaultValue: T): Preference<T> {
        return preferences.getOrPut(key) { KeyedPreference(key, defaultValue, values) } as Preference<T>
    }
}

private class KeyedPreference<T>(
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
