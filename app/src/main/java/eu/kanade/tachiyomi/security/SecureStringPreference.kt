package eu.kanade.tachiyomi.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference

/**
 * A [Preference] backed by [RayniyomiSecurePrefs] (Android Keystore AES-256-GCM) instead of
 * plaintext SharedPreferences. Used for sensitive string values such as PIN hash and salt.
 *
 * Note: [changes] only emits for mutations made through this instance. Callers of
 * [SecurityPreferences.pinHash] and [SecurityPreferences.pinSalt] only use get/set/delete,
 * so cross-instance change notification is not required.
 */
class SecureStringPreference(
    private val key: String,
    private val getter: () -> String?,
    private val setter: (String?) -> Unit,
) : Preference<String> {

    private val changeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun key(): String = key

    override fun get(): String = getter() ?: ""

    override fun set(value: String) {
        setter(value)
        changeSignal.tryEmit(Unit)
    }

    override fun isSet(): Boolean = getter() != null

    override fun delete() {
        setter(null)
        changeSignal.tryEmit(Unit)
    }

    override fun defaultValue(): String = ""

    override fun changes(): Flow<String> = changeSignal
        .onStart { emit(Unit) }
        .map { get() }
        .conflate()

    override fun stateIn(scope: CoroutineScope): StateFlow<String> =
        changes().stateIn(scope, SharingStarted.Eagerly, get())
}
