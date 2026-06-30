package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri
import androidx.lifecycle.lifecycleScope
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.domain.track.service.TrackerOAuthStateStore
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.injectLazy

class TrackLoginActivity : BaseOAuthLoginActivity() {

    private val trackPreferences: TrackPreferences by injectLazy()
    private val oauthStateStore by lazy { TrackerOAuthStateStore(trackPreferences) }

    override fun handleResult(data: Uri?) {
        val callback = TrackerOAuthCallback.from(
            host = data?.host,
            queryParameters = data.queryParameters(),
            fragment = data?.fragment,
        )
        when (val result = callback.validated(oauthStateStore::consume)) {
            TrackerOAuthCallbackResult.InvalidState -> returnToSettings()
            TrackerOAuthCallbackResult.ProviderDenied -> handleDenied(callback)
            is TrackerOAuthCallbackResult.Login -> handleLogin(result)
        }
    }

    private fun handleLogin(result: TrackerOAuthCallbackResult.Login) {
        lifecycleScope.launchIO {
            when (result.host) {
                TrackerOAuthCallback.Anilist.HOST -> trackerManager.aniList.login(result.credential)
                TrackerOAuthCallback.Bangumi.HOST -> trackerManager.bangumi.login(result.credential)
                TrackerOAuthCallback.MyAnimeList.HOST -> trackerManager.myAnimeList.login(result.credential)
                TrackerOAuthCallback.Shikimori.HOST -> trackerManager.shikimori.login(result.credential)
                TrackerOAuthCallback.Simkl.HOST -> trackerManager.simkl.login(result.credential)
            }
            returnToSettings()
        }
    }

    private fun handleDenied(callback: TrackerOAuthCallback?) {
        when (callback?.host) {
            TrackerOAuthCallback.Anilist.HOST -> trackerManager.aniList.logout()
            TrackerOAuthCallback.Bangumi.HOST -> trackerManager.bangumi.logout()
            TrackerOAuthCallback.MyAnimeList.HOST -> trackerManager.myAnimeList.logout()
            TrackerOAuthCallback.Shikimori.HOST -> trackerManager.shikimori.logout()
            TrackerOAuthCallback.Simkl.HOST -> trackerManager.simkl.logout()
        }
        returnToSettings()
    }

    private fun Uri?.queryParameters(): Map<String, String?> {
        return this?.queryParameterNames
            ?.associateWith(::getQueryParameter)
            ?: emptyMap()
    }
}
