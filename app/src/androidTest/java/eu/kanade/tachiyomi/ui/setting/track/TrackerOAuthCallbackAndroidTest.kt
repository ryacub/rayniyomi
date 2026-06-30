package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.track.service.TrackerOAuthStateStore
import eu.kanade.tachiyomi.data.track.anilist.AnilistApi
import eu.kanade.tachiyomi.data.track.bangumi.BangumiApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.data.track.shikimori.ShikimoriApi
import eu.kanade.tachiyomi.data.track.simkl.SimklApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

@RunWith(AndroidJUnit4::class)
class TrackerOAuthCallbackAndroidTest {

    @Test
    fun auth_urls_include_state_on_android_uri() {
        assertEquals("state-value", AnilistApi.authUrl("state-value").getQueryParameter("state"))
        assertEquals("state-value", MyAnimeListApi.authUrl("state-value").getQueryParameter("state"))
        assertFalse(MyAnimeListApi.authUrl("state-value").getQueryParameter("code_challenge").isNullOrBlank())
        assertEquals("state-value", BangumiApi.authUrl("state-value").getQueryParameter("state"))
        assertEquals("state-value", ShikimoriApi.authUrl("state-value").getQueryParameter("state"))
        assertEquals("state-value", SimklApi.authUrl("state-value").getQueryParameter("state"))
    }

    @Test
    fun android_uri_callback_requires_matching_one_time_state() {
        val stateStore = TrackerOAuthStateStore(
            preferences = TrackPreferences(MutablePreferenceStore()),
            generateState = { "expected-state" },
        )
        val state = stateStore.begin(TrackerOAuthCallback.MyAnimeList.HOST)
        val callback = callbackFrom(
            Uri.parse("aniyomi://myanimelist-auth?code=auth-code&state=$state"),
        )

        assertEquals(
            TrackerOAuthCallbackResult.Login(
                host = TrackerOAuthCallback.MyAnimeList.HOST,
                credential = "auth-code",
            ),
            callback.validated(stateStore::consume),
        )
        assertEquals(TrackerOAuthCallbackResult.InvalidState, callback.validated(stateStore::consume))
    }

    @Test
    fun android_uri_invalid_state_returns_before_provider_denial() {
        val stateStore = TrackerOAuthStateStore(
            preferences = TrackPreferences(MutablePreferenceStore()),
            generateState = { "expected-state" },
        )
        stateStore.begin(TrackerOAuthCallback.Simkl.HOST)
        val callback = callbackFrom(
            Uri.parse("aniyomi://simkl-auth?state=wrong-state"),
        )

        assertEquals(TrackerOAuthCallbackResult.InvalidState, callback.validated(stateStore::consume))
    }

    @Test
    fun android_uri_anilist_fragment_state_is_validated() {
        val stateStore = TrackerOAuthStateStore(
            preferences = TrackPreferences(MutablePreferenceStore()),
            generateState = { "expected-state" },
        )
        val state = stateStore.begin(TrackerOAuthCallback.Anilist.HOST)
        val callback = callbackFrom(
            Uri.parse("aniyomi://anilist-auth#access_token=token-value&token_type=Bearer&state=$state"),
        )

        assertEquals(
            TrackerOAuthCallbackResult.Login(
                host = TrackerOAuthCallback.Anilist.HOST,
                credential = "token-value",
            ),
            callback.validated(stateStore::consume),
        )
    }

    private fun callbackFrom(uri: Uri): TrackerOAuthCallback? {
        return TrackerOAuthCallback.from(
            host = uri.host,
            queryParameters = uri.queryParameterNames.associateWith(uri::getQueryParameter),
            fragment = uri.fragment,
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
