package eu.kanade.domain.extension.manga.interactor

import eu.kanade.domain.source.service.SourcePreferences
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import mihon.domain.extensionrepo.manga.repository.MangaExtensionRepoRepository
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class TrustMangaExtensionTest {

    @Test
    fun `revoke removes only the target package trust entries`() = runTest {
        val store = MutablePreferenceStore(
            initialValues = mapOf(
                "__APP_STATE_trusted_extensions" to setOf(
                    "com.example.good:1:hash-a",
                    "com.example.bad:2:hash-b",
                    "com.example.other:3:hash-c",
                ),
            ),
        )
        val preferences = SourcePreferences(store)
        val trust = createTrust(preferences)

        trust.revoke("com.example.bad")

        val trusted = preferences.trustedExtensions().get()
        trusted.shouldContain("com.example.good:1:hash-a")
        trusted.shouldContain("com.example.other:3:hash-c")
        trusted.shouldNotContain("com.example.bad:2:hash-b")
    }

    @Test
    fun `markInvalid and clearInvalid update the denylist`() = runTest {
        val store = MutablePreferenceStore()
        val preferences = SourcePreferences(store)
        val trust = createTrust(preferences)

        trust.markInvalid("com.example.bad", 42L, "hash-z")
        trust.isInvalid("com.example.bad", 42L, "hash-z").shouldBeTrue()
        trust.isInvalid("com.example.bad", 43L, "hash-z").shouldBe(false)

        trust.clearInvalid("com.example.bad")
        trust.isInvalid("com.example.bad", 42L, "hash-z").shouldBe(false)
    }

    private fun createTrust(preferences: SourcePreferences): TrustMangaExtension {
        val repo = mockk<MangaExtensionRepoRepository>()
        coEvery { repo.getAll() } returns emptyList()
        return TrustMangaExtension(
            mangaExtensionRepoRepository = repo,
            preferences = preferences,
        )
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

        override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
            MutablePreference(key, defaultValue)

        override fun <T> getObject(
            key: String,
            defaultValue: T,
            serializer: (T) -> String,
            deserializer: (String) -> T,
        ): Preference<T> = MutablePreference(key, defaultValue)

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
    }
}
