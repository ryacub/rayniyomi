package eu.kanade.domain.novel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import tachiyomi.core.common.preference.Preference

/**
 * A thin wrapper that projects a [Preference]<[Raw]> into a [Preference]<[Mapped]> view
 * via bidirectional conversion functions.
 *
 * The underlying storage always uses the [Raw] representation so the data
 * survives schema changes that add new enum values.
 *
 * @param Raw the on-disk storage type (e.g. [String]).
 * @param Mapped the in-memory domain type (e.g. [ReleaseChannel]).
 */
public class MappedPreference<Mapped>(
    private val rawPreference: Preference<String>,
    private val toMapped: (String) -> Mapped,
    private val fromMapped: (Mapped) -> String,
) : Preference<Mapped> {

    override fun key(): String = rawPreference.key()

    override fun get(): Mapped = toMapped(rawPreference.get())

    override fun set(value: Mapped) = rawPreference.set(fromMapped(value))

    override fun isSet(): Boolean = rawPreference.isSet()

    override fun delete() = rawPreference.delete()

    override fun defaultValue(): Mapped = toMapped(rawPreference.defaultValue())

    override fun changes(): Flow<Mapped> = rawPreference.changes().map(toMapped)

    override fun stateIn(scope: CoroutineScope): StateFlow<Mapped> {
        // Delegate to the underlying raw StateFlow is not directly possible without
        // a MutableStateFlow wrapper; return a mapped Flow-backed state instead by
        // falling back to the base implementation via the shared extension.
        error(
            "stateIn is not supported on MappedPreference. " +
                "Collect changes() in a viewModelScope instead.",
        )
    }
}
