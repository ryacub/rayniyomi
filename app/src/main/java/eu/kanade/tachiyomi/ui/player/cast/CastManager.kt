package eu.kanade.tachiyomi.ui.player.cast

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.google.android.gms.cast.MediaLoadOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.ui.player.settings.CastConversionPolicy
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Headers
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode

enum class CastState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

sealed class CastConversionState {
    data object Idle : CastConversionState()
    data class Prompt(val container: String) : CastConversionState()
    data class Converting(val progress: Int?) : CastConversionState()
}

sealed class CastError {
    data class LoadFailed(val reason: String) : CastError()
    data object ConnectionLost : CastError()
}

/**
 * Manages the Google Cast session lifecycle for [eu.kanade.tachiyomi.ui.player.PlayerActivity].
 * Registered as a DI singleton in [eu.kanade.tachiyomi.di.AppModule].
 */
class CastManager(
    private val context: Context,
    private val network: NetworkHelper,
    private val playerPreferences: PlayerPreferences,
    private val converter: CastVideoConverter = CastVideoConverter(context),
) {

    private val _castState = MutableStateFlow(CastState.DISCONNECTED)
    val castState: StateFlow<CastState> = _castState.asStateFlow()

    private val _castError = MutableSharedFlow<CastError>(extraBufferCapacity = 8)
    val castError: SharedFlow<CastError> = _castError.asSharedFlow()

    private val _conversionState = MutableStateFlow<CastConversionState>(CastConversionState.Idle)
    val conversionState: StateFlow<CastConversionState> = _conversionState.asStateFlow()
    private val conversionScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var conversionJob: Job? = null
    private var pendingLoad: CastLoadRequest? = null
    private var convertedFile: UniFile? = null

    private val streamProxy = CastStreamProxy(
        client = network.client,
        localFileProvider = { uri -> UniFile.fromUri(context, uri.toUri()) },
    )
    private val mediaBuilder = CastMediaBuilder(streamProxy)
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
        if (!isCastSessionActive()) {
            stopMediaPreparation()
        }
        castSession = null
    }

    // ---- Session callbacks (called by CastSessionListener) ----

    fun onSessionConnected(session: CastSession) {
        castSession = session
        _castState.value = CastState.CONNECTED
    }

    fun onSessionStarting() {
        _castState.value = CastState.CONNECTING
    }

    fun onSessionResuming() {
        _castState.value = CastState.CONNECTING
    }

    fun onSessionStartFailed() {
        stopMediaPreparation()
        castSession = null
        _castState.value = CastState.DISCONNECTED
    }

    fun onSessionEnding() {
        stopMediaPreparation()
        castSession = null
        _castState.value = CastState.DISCONNECTED
    }

    fun onSessionEnded() {
        stopMediaPreparation()
        castSession = null
        _castState.value = CastState.DISCONNECTED
    }

    fun onSessionResumeFailed() {
        stopMediaPreparation()
        castSession = null
        _castState.value = CastState.DISCONNECTED
    }

    // ---- Playback control ----

    fun loadMedia(
        video: Video,
        episode: Episode,
        anime: Anime,
        startPositionMs: Long,
        headers: Headers? = video.headers,
        playbackRate: Double = 1.0,
    ) {
        val request = CastLoadRequest(video, episode, anime, startPositionMs, headers, playbackRate)
        val session = castSession ?: return
        val client = session.remoteMediaClient ?: return

        if (CastStreamProxy.isLocalUri(video.videoUrl)) {
            when (val status = streamProxy.localMediaStatus(video.videoUrl)) {
                is CastStreamProxy.LocalMediaStatus.Castable -> loadMediaNow(request, client)
                is CastStreamProxy.LocalMediaStatus.NeedsConversion -> {
                    pendingLoad = request
                    if (playerPreferences.castConversionPolicy().get().requiresPrompt()) {
                        _conversionState.value = CastConversionState.Prompt(status.container)
                    } else {
                        startConversion()
                    }
                }
                is CastStreamProxy.LocalMediaStatus.Unavailable -> {
                    _castError.tryEmit(CastError.LoadFailed(status.reason))
                }
            }
            return
        }

        loadMediaNow(request, client)
    }

    fun startConversion(alwaysConvert: Boolean = false) {
        val request = pendingLoad ?: return
        if (alwaysConvert) {
            playerPreferences.castConversionPolicy().set(CastConversionPolicy.ALWAYS)
        }
        conversionJob?.cancel()
        conversionJob = conversionScope.launch {
            _conversionState.value = CastConversionState.Converting(null)
            try {
                val file = converter.convert(request.video.videoUrl) { progress ->
                    _conversionState.value = CastConversionState.Converting(progress)
                }
                convertedFile?.delete()
                convertedFile = file
                val convertedVideo = request.video.copy(
                    videoUrl = file.uri.toString(),
                    headers = null,
                )
                val activeSession = castSession
                val client = activeSession?.remoteMediaClient
                if (client == null) {
                    file.delete()
                    convertedFile = null
                    pendingLoad = null
                    return@launch
                }
                pendingLoad = null
                loadMediaNow(request.copy(video = convertedVideo, headers = null), client, file)
                _conversionState.value = CastConversionState.Idle
            } catch (_: CancellationException) {
                _conversionState.value = CastConversionState.Idle
            } catch (error: Exception) {
                pendingLoad = null
                _conversionState.value = CastConversionState.Idle
                _castError.tryEmit(
                    CastError.LoadFailed(error.message ?: "Could not prepare the downloaded video for casting"),
                )
            }
        }
    }

    fun cancelConversion() {
        conversionJob?.cancel()
        conversionJob = null
        pendingLoad = null
        _conversionState.value = CastConversionState.Idle
    }

    private fun loadMediaNow(
        request: CastLoadRequest,
        client: RemoteMediaClient,
        fileToKeep: UniFile? = null,
    ) {
        val video = request.video
        val episode = request.episode
        val anime = request.anime
        val startPositionMs = request.startPositionMs
        val headers = request.headers
        releaseConvertedFile(except = fileToKeep)

        val proxyHeaders = headers?.takeIf { it.size > 0 && requiresProxy(it) }
        if (proxyHeaders == null) {
            streamProxy.stop()
        }

        val mediaInfo = try {
            mediaBuilder.build(video, episode, anime, proxyHeaders)
        } catch (e: Exception) {
            releaseConvertedFile()
            _castError.tryEmit(CastError.LoadFailed(e.message ?: "Cannot cast this media"))
            return
        }

        val loadOptions = MediaLoadOptions.Builder()
            .setPlayPosition(startPositionMs)
            .setPlaybackRate(CastPlaybackRate.clamp(request.playbackRate.toFloat()).toDouble())
            .build()

        client.load(mediaInfo, loadOptions)
            .addStatusListener { status ->
                if (!status.isSuccess) {
                    _castError.tryEmit(CastError.LoadFailed("Media load failed: ${status.statusCode}"))
                    releaseConvertedFile()
                }
            }
    }

    private fun stopMediaPreparation() {
        conversionJob?.cancel()
        conversionJob = null
        pendingLoad = null
        _conversionState.value = CastConversionState.Idle
        releaseConvertedFile()
        streamProxy.stop()
    }

    private fun releaseConvertedFile(except: UniFile? = null) {
        if (convertedFile?.uri != except?.uri) {
            convertedFile?.delete()
            convertedFile = null
        }
    }

    private data class CastLoadRequest(
        val video: Video,
        val episode: Episode,
        val anime: Anime,
        val startPositionMs: Long,
        val headers: Headers?,
        val playbackRate: Double,
    )

    fun pause() {
        castSession?.remoteMediaClient?.pause()
    }

    fun play() {
        castSession?.remoteMediaClient?.play()
    }

    fun seekTo(positionMs: Long) {
        castSession?.remoteMediaClient?.seek(positionMs)
    }

    fun setPlaybackRate(rate: Double) {
        castSession?.remoteMediaClient?.setPlaybackRate(rate)
    }

    fun isPlaybackRateSupported(): Boolean {
        val status = castSession?.remoteMediaClient?.mediaStatus ?: return false
        return status.isMediaCommandSupported(MediaStatus.COMMAND_PLAYBACK_RATE)
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

    private fun requiresProxy(headers: Headers): Boolean {
        val defaultUserAgent = network.defaultUserAgentProvider()
        return headers.any { header ->
            val name = header.first
            val value = header.second
            !name.equals("User-Agent", ignoreCase = true) || value != defaultUserAgent
        }
    }

    companion object {
        private const val TAG = "CastManager"
    }
}
