package eu.kanade.tachiyomi.security

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference

internal object TranslationCredentialAccess {
    val lock = Any()
}

internal class LockedPreference<T>(
    private val delegate: Preference<T>,
) : Preference<T> {

    override fun key(): String = delegate.key()

    override fun get(): T = synchronized(TranslationCredentialAccess.lock) { delegate.get() }

    override fun set(value: T) = synchronized(TranslationCredentialAccess.lock) { delegate.set(value) }

    override fun isSet(): Boolean = synchronized(TranslationCredentialAccess.lock) { delegate.isSet() }

    override fun delete() = synchronized(TranslationCredentialAccess.lock) { delegate.delete() }

    override fun defaultValue(): T = delegate.defaultValue()

    override fun changes(): Flow<T> = delegate.changes()
        .map { synchronized(TranslationCredentialAccess.lock) { delegate.get() } }

    override fun stateIn(scope: CoroutineScope): StateFlow<T> =
        changes().stateIn(scope, SharingStarted.Eagerly, get())
}
