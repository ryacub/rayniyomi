package eu.kanade.tachiyomi.ui.player.cast

import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener

/** Bridges Cast SDK session events to [CastManager]. */
class CastSessionListener(
    private val castManager: CastManager,
) : SessionManagerListener<CastSession> {

    override fun onSessionStarted(session: CastSession, sessionId: String) {
        castManager.onSessionConnected(session)
    }

    override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
        castManager.onSessionConnected(session)
    }

    override fun onSessionEnded(session: CastSession, error: Int) {
        castManager.onSessionEnded()
    }

    override fun onSessionSuspended(session: CastSession, reason: Int) {
        castManager.onSessionEnded()
    }

    override fun onSessionResumeFailed(session: CastSession, error: Int) {
        castManager.onSessionResumeFailed()
    }

    override fun onSessionStarting(session: CastSession) = Unit
    override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
    override fun onSessionStartFailed(session: CastSession, error: Int) = Unit
    override fun onSessionEnding(session: CastSession) = Unit
}
