package eu.kanade.domain.track.service

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class TrackerOAuthStateStoreTest {

    @Test
    fun `begin stores generated state by callback host`() {
        val preferences = TrackPreferences(MutablePreferenceStore())
        val store = TrackerOAuthStateStore(preferences) { "generated-state" }

        store.begin("anilist-auth") shouldBe "generated-state"

        preferences.trackOAuthState("anilist-auth").get() shouldBe "generated-state"
    }

    @Test
    fun `consume accepts matching state once`() {
        val preferences = TrackPreferences(MutablePreferenceStore())
        val store = TrackerOAuthStateStore(preferences) { "generated-state" }
        val state = store.begin("myanimelist-auth")

        store.consume("myanimelist-auth", state).shouldBeTrue()
        store.consume("myanimelist-auth", state).shouldBeFalse()
    }

    @Test
    fun `consume rejects missing blank and mismatched state without clearing pending state`() {
        val preferences = TrackPreferences(MutablePreferenceStore())
        val store = TrackerOAuthStateStore(preferences) { "expected-state" }
        store.begin("shikimori-auth")

        store.consume("shikimori-auth", null).shouldBeFalse()
        store.consume("shikimori-auth", "").shouldBeFalse()
        store.consume("shikimori-auth", "wrong-state").shouldBeFalse()

        preferences.trackOAuthState("shikimori-auth").get() shouldBe "expected-state"
    }

    @Test
    fun `trackOAuthState uses app state key`() {
        val preferences = TrackPreferences(MutablePreferenceStore())

        preferences.trackOAuthState("simkl-auth").key() shouldBe "__APP_STATE_track_oauth_state_simkl-auth"
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
