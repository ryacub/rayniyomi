package eu.kanade.tachiyomi.ui.player.cast

import android.content.Context
import android.util.Log
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import eu.kanade.tachiyomi.animesource.model.Video
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

enum class CastState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

sealed class CastError {
    data class LoadFailed(val reason: String) : CastError()
    data object ConnectionLost : CastError()
}

/**
 * Manages the Google Cast session lifecycle for [eu.kanade.tachiyomi.ui.player.PlayerActivity].
 * Registered as a DI singleton in [eu.kanade.tachiyomi.di.AppModule].
 */
class CastManager(private val context: Context) {

    private val _castState = MutableStateFlow(CastState.DISCONNECTED)
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    private val _castError = MutableSharedFlow<CastError>(extraBufferCapacity = 8)
    val castError: SharedFlow<CastError> = _castError.asSharedFlow()

    private val mediaBuilder = CastMediaBuilder()
    private val sessionListener = CastSessionListener(this)

    private var castSession: CastSession? = null
    private var sessionManager: SessionManager? = null
    private var isActivityRegistered = false

    // ---- Lifecycle ----

    /** Call in [PlayerActivity.onCreate] to clear stale session references from a previous Activity instance. */
    fun resetForNewActivity() {
        castSession = null
        _castState.value = CastState.DISCONNECTED
    }

    /** Call in [PlayerActivity.onStart] — idempotent listener registration. */
    fun registerActivity() {
        if (isActivityRegistered) return
        val sm = getSessionManager()
        if (sm == null) {
            Log.w(TAG, "Cast Framework unavailable; Cast features disabled for this session")
            return
        }
        sessionManager = sm
        sm.addSessionManagerListener(sessionListener, CastSession::class.java)
        isActivityRegistered = true
        // Restore state if a session is already active (e.g. after config change)
        sm.currentCastSession?.let { onSessionConnected(it) }
    }

    /** Call in [PlayerActivity.onPause] — stops listening but keeps the Cast session alive. */
    fun unregisterActivity() {
        sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        sessionManager = null
        isActivityRegistered = false
    }

    /** Call in [PlayerActivity.onDestroy]. */
    fun cleanup() {
        unregisterActivity()
        castSession = null
    }

    // ---- Session callbacks (called by CastSessionListener) ----

    fun onSessionConnected(session: CastSession) {
        castSession = session
        _castState.value = CastState.CONNECTED
    }

    fun onSessionEnded() {
        castSession = null
        _castState.value = CastState.DISCONNECTED
    }

    fun onSessionResumeFailed() {
        castSession = null
        _castState.value = CastState.DISCONNECTED
    }

    // ---- Playback control ----

    fun loadMedia(
        video: Video,
        episode: Episode,
        anime: Anime,
        startPositionMs: Long,
    ) {
        val session = castSession ?: return
        val client = session.remoteMediaClient ?: return

        val mediaInfo = try {
            mediaBuilder.build(video, episode, anime)
        } catch (e: IllegalStateException) {
            _castError.tryEmit(CastError.LoadFailed(e.message ?: "Cannot cast local files"))
            return
        }

        val loadOptions = com.google.android.gms.cast.MediaLoadOptions.Builder()
            .setPlayPosition(startPositionMs)
            .build()

        client.load(mediaInfo, loadOptions)
            .addStatusListener { status ->
                if (!status.isSuccess) {
                    _castError.tryEmit(CastError.LoadFailed("Media load failed: ${status.statusCode}"))
                }
            }
    }

    fun pause() {
        castSession?.remoteMediaClient?.pause()
    }

    fun play() {
        castSession?.remoteMediaClient?.play()
    }

    fun seekTo(positionMs: Long) {
        castSession?.remoteMediaClient?.seek(positionMs)
    }

    fun disconnect() {
        getSessionManager()?.endCurrentSession(true)
    }

    fun isCastSessionActive(): Boolean = castSession != null && _castState.value == CastState.CONNECTED

    fun getRemoteMediaClient() = castSession?.remoteMediaClient

    // ---- Internal ----

    private fun getSessionManager(): SessionManager? {
        return try {
            CastContext.getSharedInstance(context).sessionManager
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Cast SessionManager: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "CastManager"
    }
}
