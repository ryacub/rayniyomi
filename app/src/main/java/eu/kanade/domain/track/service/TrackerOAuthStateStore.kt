package eu.kanade.domain.track.service

import eu.kanade.tachiyomi.util.PkceUtil

class TrackerOAuthStateStore(
    private val preferences: TrackPreferences,
    private val generateState: () -> String = PkceUtil::generateCodeVerifier,
) {

    fun begin(callbackHost: String): String {
        val state = generateState()
        preferences.trackOAuthState(callbackHost).set(state)
        return state
    }

    fun consume(callbackHost: String, returnedState: String?): Boolean {
        if (returnedState.isNullOrBlank()) return false

        val pendingState = preferences.trackOAuthState(callbackHost)
        val expectedState = pendingState.get()
        if (expectedState.isBlank() || expectedState != returnedState) {
            return false
        }

        pendingState.delete()
        return true
    }
}
