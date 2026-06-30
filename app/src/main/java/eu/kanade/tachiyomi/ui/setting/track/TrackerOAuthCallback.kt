package eu.kanade.tachiyomi.ui.setting.track

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

sealed interface TrackerOAuthCallback {
    val host: String
    val credential: String?
    val state: String?

    data class Anilist(
        val accessToken: String?,
        override val state: String?,
    ) : TrackerOAuthCallback {
        override val credential: String?
            get() = accessToken

        override val host: String = HOST

        companion object {
            const val HOST = "anilist-auth"
        }
    }

    data class Bangumi(
        val code: String?,
        override val state: String?,
    ) : TrackerOAuthCallback {
        override val credential: String?
            get() = code

        override val host: String = HOST

        companion object {
            const val HOST = "bangumi-auth"
        }
    }

    data class MyAnimeList(
        val code: String?,
        override val state: String?,
    ) : TrackerOAuthCallback {
        override val credential: String?
            get() = code

        override val host: String = HOST

        companion object {
            const val HOST = "myanimelist-auth"
        }
    }

    data class Shikimori(
        val code: String?,
        override val state: String?,
    ) : TrackerOAuthCallback {
        override val credential: String?
            get() = code

        override val host: String = HOST

        companion object {
            const val HOST = "shikimori-auth"
        }
    }

    data class Simkl(
        val code: String?,
        override val state: String?,
    ) : TrackerOAuthCallback {
        override val credential: String?
            get() = code

        override val host: String = HOST

        companion object {
            const val HOST = "simkl-auth"
        }
    }

    companion object {
        fun from(
            host: String?,
            queryParameters: Map<String, String?>,
            fragment: String?,
        ): TrackerOAuthCallback? {
            val fragmentParameters = fragmentParameters(fragment)
            return when (host) {
                Anilist.HOST -> Anilist(
                    accessToken = fragmentParameters["access_token"],
                    state = fragmentParameters["state"],
                )
                Bangumi.HOST -> Bangumi(
                    code = queryParameters["code"],
                    state = queryParameters["state"],
                )
                MyAnimeList.HOST -> MyAnimeList(
                    code = queryParameters["code"],
                    state = queryParameters["state"],
                )
                Shikimori.HOST -> Shikimori(
                    code = queryParameters["code"],
                    state = queryParameters["state"],
                )
                Simkl.HOST -> Simkl(
                    code = queryParameters["code"],
                    state = queryParameters["state"],
                )
                else -> null
            }
        }

        private fun fragmentParameters(fragment: String?): Map<String, String> {
            if (fragment.isNullOrBlank()) return emptyMap()

            return fragment.split("&")
                .mapNotNull { parameter ->
                    val separator = parameter.indexOf("=")
                    if (separator <= 0) return@mapNotNull null

                    val key = parameter.substring(0, separator).decodeUrlParameter()
                    val value = parameter.substring(separator + 1).decodeUrlParameter()
                    key to value
                }
                .toMap()
        }

        private fun String.decodeUrlParameter(): String {
            return URLDecoder.decode(this, StandardCharsets.UTF_8.name())
        }
    }
}

sealed interface TrackerOAuthCallbackResult {
    data class Login(
        val host: String,
        val credential: String,
    ) : TrackerOAuthCallbackResult

    data object ProviderDenied : TrackerOAuthCallbackResult

    data object InvalidState : TrackerOAuthCallbackResult
}

fun TrackerOAuthCallback?.validated(
    consumeState: (host: String, state: String?) -> Boolean,
): TrackerOAuthCallbackResult {
    val callback = this ?: return TrackerOAuthCallbackResult.InvalidState
    if (callback.state.isNullOrBlank()) {
        return TrackerOAuthCallbackResult.InvalidState
    }
    if (!consumeState(callback.host, callback.state)) {
        return TrackerOAuthCallbackResult.InvalidState
    }

    val credential = callback.credential
    return if (credential.isNullOrBlank()) {
        TrackerOAuthCallbackResult.ProviderDenied
    } else {
        TrackerOAuthCallbackResult.Login(
            host = callback.host,
            credential = credential,
        )
    }
}
