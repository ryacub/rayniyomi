package eu.kanade.domain.novel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.Preference

/**
 * Wraps a [String] preference to expose a nullable [Long] API.
 *
 * An empty string sentinel (`""`) represents the "not set" / `null` state.
 * This avoids taking a dependency on optional/nullable preference storage
 * that the core [PreferenceStore] contract does not provide.
 */
public class NullableLongPreference(
    private val rawPreference: Preference<String>,
) : Preference<Long?> {

    override fun key(): String = rawPreference.key()

    override fun get(): Long? = rawPreference.get().toLongOrNull()

    override fun set(value: Long?) = rawPreference.set(value?.toString() ?: "")

    override fun isSet(): Boolean = rawPreference.get().isNotEmpty()

    override fun delete() = rawPreference.delete()

    override fun defaultValue(): Long? = null

    override fun changes(): Flow<Long?> = rawPreference.changes().map { it.toLongOrNull() }

    override fun stateIn(scope: CoroutineScope): StateFlow<Long?> {
        error(
            "stateIn is not supported on NullableLongPreference. " +
                "Collect changes() in a viewModelScope instead.",
        )
    }
}
