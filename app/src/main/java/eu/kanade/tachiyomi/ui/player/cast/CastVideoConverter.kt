package eu.kanade.tachiyomi.ui.player.cast

import android.content.Context
import androidx.core.net.toUri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.util.storage.toFFmpegString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

class CastVideoConverter(
    private val context: Context,
) {

    suspend fun convert(
        videoUrl: String,
        onProgress: (Int) -> Unit,
    ): UniFile = withContext(Dispatchers.IO) {
        val input = UniFile.fromUri(context, videoUrl.toUri())
            ?.takeIf { it.exists() && it.isFile }
            ?: error("Downloaded video is not available: $videoUrl")
        val durationMs = probeDuration(input)
        val temporaryFile = File.createTempFile("rayniyomi-cast-", ".mp4.part", context.cacheDir)
        val outputFile = File(temporaryFile.parentFile, temporaryFile.name.removeSuffix(".part"))
        temporaryFile.delete()

        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val ffmpegSession = FFmpegKit.executeWithArgumentsAsync(
                    buildArguments(input.toFFmpegString(context), temporaryFile.absolutePath),
                    { result ->
                        if (result.returnCode.isValueSuccess) {
                            if (continuation.isActive) continuation.resume(Unit)
                        } else if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(Exception(result.output.orEmpty())))
                        }
                    },
                    null,
                    { statistics ->
                        if (durationMs != null && durationMs > 0) {
                            val progress = (statistics.time * 1000 / durationMs)
                                .toInt()
                                .coerceIn(0, 99)
                            onProgress(progress)
                        }
                    },
                )
                continuation.invokeOnCancellation { ffmpegSession.cancel() }
            }
            check(temporaryFile.exists() && temporaryFile.length() > 0) {
                "Cast conversion produced an empty file"
            }
            check(temporaryFile.renameTo(outputFile)) {
                "Could not finalize the Cast conversion"
            }
            onProgress(100)
            return@withContext checkNotNull(UniFile.fromFile(outputFile)) {
                "Could not access the Cast conversion output"
            }
        } catch (error: Throwable) {
            temporaryFile.delete()
            outputFile.delete()
            throw error
        }
    }

    private suspend fun probeDuration(file: UniFile): Long? {
        return suspendCancellableCoroutine { continuation ->
            val session = FFprobeKit.executeWithArgumentsAsync(
                FFmpegKitConfig.parseArguments(
                    "-v quiet -show_entries format=duration " +
                        "-of default=noprint_wrappers=1:nokey=1 \"${file.toFFmpegString(context)}\"",
                ),
            ) { result ->
                if (continuation.isActive) {
                    continuation.resume(
                        result.output?.trim()?.toDoubleOrNull()?.times(1000)?.toLong(),
                    )
                }
            }
            continuation.invokeOnCancellation { session.cancel() }
        }
    }

    companion object {
        internal fun buildArguments(input: String, output: String): Array<String> {
            return arrayOf(
                "-y",
                "-i",
                input,
                "-map",
                "0:v:0",
                "-map",
                "0:a:0?",
                "-c",
                "copy",
                "-sn",
                "-dn",
                "-movflags",
                "+faststart",
                "-f",
                "mp4",
                output,
            )
        }
    }
}
