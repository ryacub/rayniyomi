package eu.kanade.tachiyomi.ui.updates

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

internal class InMemoryPreferenceStore(
    initialValues: Map<String, Any?> = emptyMap(),
) : PreferenceStore {

    private val values = initialValues.toMutableMap()

    // A revision counter per key, shared by every Preference made for that key.
    // Each write or delete bumps the counter, which is how one wrapper tells
    // every other wrapper of that key that the stored value moved.
    // AndroidPreferenceStore behaves the same way: each caller gets its own
    // wrapper with its own default value, but a write through one wrapper is
    // visible to all of them. Caching the Preference objects instead would make
    // the first caller's default value win for every later caller, which
    // silently changes what an unset key reads as.
    private val revisions = mutableMapOf<String, MutableStateFlow<Int>>()

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

    private fun <T> getPreference(key: String, defaultValue: T): Preference<T> {
        val revision = revisions.getOrPut(key) { MutableStateFlow(0) }
        return KeyedPreference(key, defaultValue, values, revision)
    }
}

private class KeyedPreference<T>(
    private val key: String,
    private val defaultValue: T,
    private val values: MutableMap<String, Any?>,
    private val revision: MutableStateFlow<Int>,
) : Preference<T> {

    override fun key(): String = key

    override fun get(): T = currentValue()

    override fun set(value: T) {
        values[key] = value
        revision.value++
    }

    override fun isSet(): Boolean = values.containsKey(key)

    override fun delete() {
        values.remove(key)
        revision.value++
    }

    override fun defaultValue(): T = defaultValue

    // revision is a StateFlow, so a new collector receives the current value
    // immediately and then every later write to this key.
    override fun changes(): Flow<T> = revision.map { currentValue() }

    override fun stateIn(scope: CoroutineScope): StateFlow<T> =
        changes().stateIn(scope, SharingStarted.Eagerly, currentValue())

    @Suppress("UNCHECKED_CAST")
    private fun currentValue(): T = values.getOrDefault(key, defaultValue) as T
}
