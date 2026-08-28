package eu.kanade.tachiyomi.data.download.anime

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.LogCallback
import com.arthenica.ffmpegkit.LogRedirectionStrategy
import com.arthenica.ffmpegkit.StatisticsCallback
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.anime.multithread.DownloadError
import eu.kanade.tachiyomi.data.download.anime.multithread.DownloadResult
import eu.kanade.tachiyomi.data.download.anime.multithread.MultiThreadDownloader
import eu.kanade.tachiyomi.data.download.anime.multithread.VideoFormat
import eu.kanade.tachiyomi.data.download.anime.multithread.VideoSignatureValidator
import eu.kanade.tachiyomi.data.download.anime.strategy.DownloadStrategy
import eu.kanade.tachiyomi.data.download.anime.strategy.DownloadStrategySelector
import eu.kanade.tachiyomi.data.download.core.DownloadFailureClassifier
import eu.kanade.tachiyomi.data.download.core.LowStorageException
import eu.kanade.tachiyomi.data.download.core.RetriesExhaustedException
import eu.kanade.tachiyomi.data.download.core.StoragePermissionException
import eu.kanade.tachiyomi.data.download.model.DownloadBlockedReason
import eu.kanade.tachiyomi.data.download.model.DownloadDisplayStatus
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fetches an anime video with the selected strategy and retry policy.
 *
 * Queue orchestration, external downloader hand-off, and final output validation stay in
 * [AnimeDownloader].
 */
class AnimeVideoDownloader(
    private val context: Context,
    private val strategySelector: DownloadStrategySelector,
    private val multiThreadDownloader: MultiThreadDownloader,
    private val preferences: DownloadPreferences,
    private val onLowStorage: (AnimeDownload, String?) -> Unit,
) {

    /** Base backoff for video download retries. Tests set this to zero to avoid real delays. */
    @VisibleForTesting
    internal var retryBackoffMillis: Long = 2_000L

    suspend fun download(
        download: AnimeDownload,
        tmpDir: UniFile,
        filename: String,
    ): UniFile {
        return flow {
            tmpDir.findFile("$filename.tmp")?.delete()
            val videoFile = tmpDir.createFile("$filename.tmp")!!
            try {
                when (selectDownloadStrategy(download)) {
                    DownloadStrategy.MULTI_THREAD -> {
                        downloadWithMultiThread(download, tmpDir, videoFile, filename)
                    }
                    DownloadStrategy.SINGLE_THREAD,
                    DownloadStrategy.FFMPEG,
                    -> {
                        ffmpegDownload(download, tmpDir, videoFile, filename)
                    }
                }
            } catch (e: Exception) {
                videoFile.delete()
                throw e
            }

            emit(videoFile)
        }
            .retryWhen { cause, attempt ->
                if (cause is LowStorageException) {
                    return@retryWhen false
                }
                if (DownloadFailureClassifier.isPermissionFailure(cause)) {
                    throw StoragePermissionException(
                        cause.message ?: cause::class.simpleName,
                        cause,
                    )
                }
                if (attempt < 3) {
                    download.retryAttempt = attempt.toInt() + 1
                    download.displayStatus = DownloadDisplayStatus.RETRYING
                    delay(retryBackoffMillis shl attempt.toInt())
                    true
                } else {
                    throw RetriesExhaustedException(cause)
                }
            }
            .flowOn(Dispatchers.IO)
            .first()
    }

    private suspend fun selectDownloadStrategy(download: AnimeDownload): DownloadStrategy {
        download.displayStatus = DownloadDisplayStatus.PREPARING
        download.blockedReason = DownloadBlockedReason.PREPARING
        val video = download.video!!
        val videoUrl = video.videoUrl
            ?: return DownloadStrategy.FFMPEG

        val format = VideoSignatureValidator.detectVideoFormat(videoUrl)
        if (format == VideoFormat.HLS || format == VideoFormat.DASH) {
            return DownloadStrategy.FFMPEG
        }

        val multiThreadEnabled = preferences.multiThreadDownloads().get()
        val maxConnections = preferences.multiThreadConnections().get().coerceIn(1, 4)
        val result = strategySelector.selectStrategy(
            videoUrl = videoUrl,
            headers = video.headers?.let {
                okhttp3.Headers.headersOf(*it.toList().flatMap { (k, v) -> listOf(k, v) }.toTypedArray())
            },
            multiThreadEnabled = multiThreadEnabled,
            maxConnections = maxConnections,
        )

        return when (result) {
            is DownloadStrategySelector.StrategyResult.Success -> result.strategy
            is DownloadStrategySelector.StrategyResult.Error -> {
                logcat(LogPriority.WARN) { "Strategy selection failed: ${result.reason}, falling back to FFmpeg" }
                DownloadStrategy.FFMPEG
            }
        }
    }

    private suspend fun downloadWithMultiThread(
        download: AnimeDownload,
        tmpDir: UniFile,
        videoFile: UniFile,
        filename: String,
    ) {
        val video = download.video!!
        val episodeId = download.episode.id ?: throw IllegalStateException("Episode ID is null")
        val videoUrl = video.videoUrl
            ?: throw IllegalStateException("Video URL is null for episode $episodeId")
        val extension = VideoSignatureValidator.detectVideoFormat(videoUrl).extension

        val result = multiThreadDownloader.download(
            episodeId = episodeId,
            videoUrl = videoUrl,
            headers = video.headers?.let {
                okhttp3.Headers.headersOf(*it.toList().flatMap { (k, v) -> listOf(k, v) }.toTypedArray())
            },
            tmpDir = tmpDir,
            outputFile = videoFile,
        ) { progress ->
            download.progress = progress.progressPercent.coerceIn(0, 100)
            download.lastProgressAt = System.currentTimeMillis()
            download.displayStatus = DownloadDisplayStatus.DOWNLOADING
            download.retryAttempt = 0
        }

        when (result) {
            is DownloadResult.Success -> {
                tmpDir.findFile("$filename.tmp")?.apply {
                    renameTo("$filename.$extension")
                }
            }
            is DownloadResult.Error -> {
                when (val error = result.error) {
                    is DownloadError.InvalidRange -> {
                        if (error.msg.contains("not satisfiable", ignoreCase = true)) {
                            download.displayStatus = DownloadDisplayStatus.RETRYING
                            download.lastErrorCode = "RANGE_NOT_SATISFIABLE"
                            download.lastErrorReason = "Remote file changed, restarting download"
                        } else {
                            download.lastErrorCode = "RANGE_UNSUPPORTED"
                            download.lastErrorReason = "Server does not support range requests, falling back"
                        }
                        ffmpegDownload(download, tmpDir, videoFile, filename)
                        return
                    }
                    is DownloadError.DiskFull -> {
                        pauseForLowStorage(download, error.message)
                        throw LowStorageException(error.message ?: "Insufficient storage")
                    }
                    else -> {
                        download.lastErrorCode = error::class.simpleName
                        download.lastErrorReason = error.message
                    }
                }
                throw Exception("Multi-thread download failed: ${result.error.message}")
            }
            DownloadResult.Cancelled -> {
                throw CancellationException("Download cancelled")
            }
        }
    }

    private suspend fun ffmpegDownload(
        download: AnimeDownload,
        tmpDir: UniFile,
        videoFile: UniFile,
        filename: String,
    ) {
        val video = download.video!!
        val ffmpegFilename = { videoFile.uri.toFFmpegString(context) }
        val headers = video.headers ?: download.source.headers
        val headerOptions = headers.joinToString("", "-headers '", "'") {
            "${it.first}: ${it.second}\r\n"
        }

        FFmpegKitConfig.setLogRedirectionStrategy(LogRedirectionStrategy.ALWAYS_PRINT_LOGS)
        val ffmpegOptions = getFFmpegOptions(video, headerOptions, ffmpegFilename())
        val ffprobeCommand = { file: String, ffprobeHeaders: String? ->
            FFmpegKitConfig.parseArguments(
                "${ffprobeHeaders?.plus(" ") ?: ""}-v quiet -show_entries " +
                    "format=duration -of default=noprint_wrappers=1:nokey=1 \"$file\"",
            )
        }

        var duration = 0L
        val logCallback = LogCallback { log ->
            if (log.level <= Level.AV_LOG_WARNING) {
                log.message?.let { logcat(LogPriority.ERROR) { it } }
            }
        }
        val statCallback = StatisticsCallback { s ->
            val outTime = (s.time / 1000.0).toLong()
            if (duration != 0L && outTime > 0) {
                download.progress = (100 * outTime / duration).toInt()
                download.lastProgressAt = System.currentTimeMillis()
                download.displayStatus = DownloadDisplayStatus.DOWNLOADING
            }
        }

        duration = getDuration(ffprobeCommand(video.videoUrl, headerOptions))?.toLong() ?: 0L

        suspendCancellableCoroutine { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                ffmpegOptions,
                {
                    if (it.returnCode.isValueSuccess) {
                        tmpDir.findFile("$filename.tmp")?.apply {
                            renameTo("$filename.mkv")
                        }
                        continuation.resume(it)
                    } else {
                        val output = it.output
                        if (DownloadFailureClassifier.isLowStorageFailure(output)) {
                            pauseForLowStorage(download, output)
                            continuation.resumeWithException(
                                LowStorageException(output ?: "Insufficient storage"),
                            )
                        } else {
                            continuation.resumeWithException(
                                Exception("Error in ffmpeg! ${output.orEmpty()}"),
                            )
                        }
                    }
                },
                logCallback,
                statCallback,
            )
            continuation.invokeOnCancellation { session.cancel() }
        }
    }

    private fun pauseForLowStorage(download: AnimeDownload, message: String?) {
        onLowStorage(download, message)
    }

    private fun getFFmpegOptions(video: Video, headerOptions: String, ffmpegFilename: String): Array<String> {
        fun formatInputs(tracks: List<Track>) = tracks.joinToString(" ", postfix = " ") {
            buildList {
                if (it.url.startsWith("http")) add(headerOptions)
                add("-i")
                add("\"${it.url}\"")
            }.joinToString(" ")
        }
        fun formatMaps(tracks: List<Track>, type: String, offset: Int = 0) = tracks.indices.joinToString(" ") {
            "-map ${it + 1 + offset}:$type"
        }
        fun formatMetadata(tracks: List<Track>, type: String) = tracks.mapIndexed { i, track ->
            "-metadata:s:$type:$i \"title=${track.lang}\""
        }.joinToString(" ")

        val subtitleInputs = formatInputs(video.subtitleTracks)
        val subtitleMaps = formatMaps(video.subtitleTracks, "s")
        val subtitleMetadata = formatMetadata(video.subtitleTracks, "s")
        val audioInputs = formatInputs(video.audioTracks)
        val audioMaps = formatMaps(video.audioTracks, "a", video.subtitleTracks.size)
        val audioMetadata = formatMetadata(video.audioTracks, "a")
        val sourceStreamOptions = video.ffmpegStreamArgs.joinToString(" ") { (key, value) ->
            "-$key \"$value\""
        }
        val sourceVideoOptions = video.ffmpegVideoArgs.joinToString(" ") { (key, value) ->
            "-$key \"$value\""
        }
        val videoInput = buildList {
            if (video.videoUrl.startsWith("http")) add(headerOptions)
            add(sourceStreamOptions)
            add("-i")
            add("\"${video.videoUrl}\"")
        }.joinToString(" ")
        val command = listOf(
            videoInput, subtitleInputs, audioInputs,
            "-map 0:v", audioMaps, "-map 0:a?", subtitleMaps, "-map 0:s? -map 0:t?",
            "-f matroska -c:a copy -c:v copy -c:s copy",
            subtitleMetadata, audioMetadata, sourceVideoOptions,
            "\"$ffmpegFilename\" -y",
        )
            .filter(String::isNotBlank)
            .joinToString(" ")

        return FFmpegKitConfig.parseArguments(command)
    }

    private suspend fun getDuration(ffprobeCommand: Array<String>): Float? {
        return suspendCancellableCoroutine { continuation ->
            val session = FFprobeKit.executeWithArgumentsAsync(ffprobeCommand) {
                if (it.returnCode.isValueSuccess) {
                    continuation.resume(it)
                } else {
                    continuation.resumeWithException(Exception(it.output))
                }
            }
            continuation.invokeOnCancellation { session.cancel() }
        }.output.toFloatOrNull()
    }
}
