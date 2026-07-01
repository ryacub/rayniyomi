package eu.kanade.tachiyomi.ui.setting.track

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TrackerOAuthCallbackTest {

    @Test
    fun `anilist callback reads token and state from fragment`() {
        val callback = TrackerOAuthCallback.from(
            host = "anilist-auth",
            queryParameters = emptyMap(),
            fragment = "access_token=token-value&token_type=Bearer&state=state-value",
        )

        callback shouldBe TrackerOAuthCallback.Anilist(
            accessToken = "token-value",
            state = "state-value",
        )
    }

    @Test
    fun `code callback reads code and state from query`() {
        val callback = TrackerOAuthCallback.from(
            host = "myanimelist-auth",
            queryParameters = mapOf("code" to "auth-code", "state" to "state-value"),
            fragment = null,
        )

        callback shouldBe TrackerOAuthCallback.MyAnimeList(
            code = "auth-code",
            state = "state-value",
        )
    }

    @Test
    fun `valid state with missing code is provider denial`() {
        val result = TrackerOAuthCallback.from(
            host = "bangumi-auth",
            queryParameters = mapOf("state" to "state-value"),
            fragment = null,
        ).validated { host, state ->
            host shouldBe "bangumi-auth"
            state shouldBe "state-value"
            true
        }

        result shouldBe TrackerOAuthCallbackResult.ProviderDenied
    }

    @Test
    fun `missing or mismatched state is invalid before provider action`() {
        val missingState = TrackerOAuthCallback.from(
            host = "simkl-auth",
            queryParameters = mapOf("code" to "auth-code"),
            fragment = null,
        ).validated { _, _ -> true }

        val mismatchedState = TrackerOAuthCallback.from(
            host = "shikimori-auth",
            queryParameters = mapOf("code" to "auth-code", "state" to "wrong-state"),
            fragment = null,
        ).validated { _, _ -> false }

        missingState shouldBe TrackerOAuthCallbackResult.InvalidState
        mismatchedState shouldBe TrackerOAuthCallbackResult.InvalidState
    }

    @Test
    fun `valid state with credential is login`() {
        val result = TrackerOAuthCallback.from(
            host = "shikimori-auth",
            queryParameters = mapOf("code" to "auth-code", "state" to "state-value"),
            fragment = null,
        ).validated { _, _ -> true }

        result shouldBe TrackerOAuthCallbackResult.Login(
            host = "shikimori-auth",
            credential = "auth-code",
        )
    }
}
