package eu.kanade.tachiyomi.ui.player

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.di.appGraph
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.source.local.entries.anime.LocalAnimeSource
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the production player and tracker against the pinned native MPV library. */
@RunWith(AndroidJUnit4::class)
class PlayerNativePlaybackAndroidTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val trace = NativeTrace()
    private val tempFiles = mutableListOf<File>()
    private var scenario: ActivityScenario<PlayerActivity>? = null
    private var animeId: Long? = null

    @Test
    fun reopeningEpisodeRestoresManualServerAfterReorder() = runBlocking {
        val mediaA = mediaFile()
        val mediaB = mediaFile()
        val mediaC = mediaFile()
        val replacementB = mediaFile()
        val episode = createEpisode()
        val hosterA = Hoster(hosterName = "A", videoList = listOf(video(mediaA, "A")))
        val hosterB = Hoster(hosterName = "B", videoList = listOf(video(mediaB, "B")))
        val hosterC = Hoster(hosterName = "C", videoList = listOf(video(mediaC, "C")))
        launchPlayer(episode, listOf(hosterA, hosterB, hosterC))
        trace.awaitEvent(MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED, mediaA)
        val player = playerViewModel()
        instrumentation.runOnMainSync { player.onVideoClicked(1, 0) }
        trace.awaitEvent(MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED, mediaB)

        scenario?.close()
        val changedB = hosterB.copy(videoList = listOf(video(replacementB, "B")))
        launchPlayer(episode, listOf(hosterC, hosterA, changedB))

        val reopenedPlayer = playerViewModel()
        awaitCondition("Reopened episode did not select a video") { reopenedPlayer.currentVideo.value != null }
        assertEquals("B", reopenedPlayer.currentVideo.value?.videoTitle)
        trace.awaitEvent(MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED, replacementB)

        scenario?.close()
        val fallback = mediaFile()
        val failedMedia = missingFile("remembered")
        val missingB = hosterB.copy(videoList = listOf(video(failedMedia, "B")))
        val fallbackA = hosterA.copy(videoList = listOf(video(fallback, "A")))
        launchPlayer(episode, listOf(fallbackA, missingB))
        trace.awaitEvent(MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED, fallback)
        assertTrue(
            "MPV did not report that the remembered server failed to open",
            trace.logs.any { it.startsWith("Cannot open file '${failedMedia.path}'") },
        )

        scenario?.close()
        val returnedB = mediaFile()
        launchPlayer(episode, listOf(hosterA, hosterB.copy(videoList = listOf(video(returnedB, "B")))))
        trace.awaitEvent(MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED, returnedB)

        scenario?.close()
        val otherEpisode = addEpisode(episode.animeId, 2.0)
        val otherA = mediaFile()
        launchPlayer(otherEpisode, listOf(hosterA.copy(videoList = listOf(video(otherA, "A"))), hosterB))
        trace.awaitEvent(MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED, otherA)
    }

    @Test
    fun failedFirstUrlLoadsFallbackAndAdvancesPlayback() = runBlocking {
        val media = mediaFile()
        val missing = missingFile("first")
        launchPlayer(listOf(video(missing), video(media)))

        trace.awaitEvent(MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED, media)
        val player = playerViewModel()
        instrumentation.runOnMainSync { player.unpause() }
        val loadedPosition = trace.awaitPositionAfter(0.5)
        assertTrue("Fallback MPV time-pos did not advance: $loadedPosition", loadedPosition > 0.5)
        val nativeError = trace.errors.singleOrNull()
        val fallbackLoad = trace.events.first {
            it.eventId == MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED && it.path?.sameFile(media) == true
        }
        assertTrue(
            "MPV reported no file-open error before fallback: ${trace.snapshot()}",
            nativeError != null && nativeError.message == "loading failed" &&
                nativeError.sequence < fallbackLoad.sequence &&
                trace.logs.any { it.startsWith("Cannot open file '${missing.path}'") },
        )
        assertTrue(
            "Fallback did not become the current production video",
            player.currentVideo.value?.videoUrl == media.toURI().toString(),
        )

        if (InstrumentationRegistry.getArguments().getString("r1176.mcpHold") == "true") {
            holdForMcpSmoke()
        }
    }

    @Test
    fun rapidReplacementWhileUnloadIsHeldRecoversFromNativeCFailure() = runBlocking {
        val mediaA = mediaFile()
        val mediaB = mediaFile()
        val missingC = missingFile("rapid-c")
        val videoB = video(mediaB, title = "B")
        val videoC = video(missingC, title = "C")
        launchPlayer(listOf(video(mediaA, title = "A"), videoB, videoC))

        trace.awaitEvent(MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED, mediaA)
        val player = playerViewModel()
        loadUnloadGate()
        MPVLib.command(arrayOf("script-message", "r1176-hold-unload"))
        awaitCondition("MPV unload gate did not arm") { trace.logs.any { it.contains("R1176_NATIVE gate ARMED") } }

        val eventCountBeforeB = trace.events.size
        clickVideo(player, 1, "B")
        awaitCondition("A unload hook did not hold") {
            trace.logs.any { it.contains("R1176_NATIVE gate HELD") }
        }
        awaitPlaylistFile(videoB)
        clickVideo(player, 2, "C")
        withTimeout(LOAD_TIMEOUT_MS) {
            player.currentVideo.first { it?.videoUrl == missingC.toURI().toString() }
        }
        assertTrue("C was not selected before A unload resumed", player.currentVideo.value?.videoTitle == "C")
        assertTrue(
            "C was issued before B received native START_FILE",
            MPVLib.getPropertyString("playlist/0/filename")?.sameFile(mediaB) == true,
        )
        assertFalse(
            "B started before the held A unload resumed: ${trace.snapshot()}",
            trace.hasStartFor(mediaB, after = eventCountBeforeB),
        )

        assertEquals(
            "A held unload allowed another native START_FILE",
            0,
            trace.events.drop(eventCountBeforeB).count {
                it.eventId ==
                    MPVLib.mpvEventId.MPV_EVENT_START_FILE
            },
        )
        val eventCountBeforeRecovery = trace.events.size
        releaseUnloadGate()
        val cError = trace.awaitError(missingC)
        assertEquals(
            "Native replacements did not start in order before C failed",
            2,
            trace.events.drop(eventCountBeforeB).count {
                it.eventId ==
                    MPVLib.mpvEventId.MPV_EVENT_START_FILE &&
                    it.sequence < cError.sequence
            },
        )
        trace.awaitEvent(MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED, mediaA, after = eventCountBeforeRecovery)
        assertTrue(
            "Native C failure did not cause fallback to A: ${trace.snapshot()}",
            player.currentVideo.value?.videoTitle == "A",
        )
        assertTrue("B never received its native START_FILE: ${trace.snapshot()}", trace.hasStartFor(mediaB))
    }

    @Test
    fun manualFailedSelectionKeepsPositionPausedUntilExplicitResume() = runBlocking {
        val media = mediaFile()
        val missing = missingFile("manual")
        launchPlayer(listOf(video(media, title = "A"), video(missing, title = "B")))

        trace.awaitEvent(MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED, media)
        val player = playerViewModel()
        awaitCondition("Initial player setup did not complete") { !player.isLoadingEpisode.value }
        instrumentation.runOnMainSync { }
        loadUnloadGate()
        instrumentation.runOnMainSync { player.unpause() }
        MPVLib.command(arrayOf("seek", "5", "absolute+exact"))
        awaitPositionAtLeast(4.8)
        instrumentation.runOnMainSync { player.pause() }
        awaitCondition("MPV did not pause") { MPVLib.getPropertyBoolean("pause") == true }
        val retainedPosition = MPVLib.getPropertyDouble("time-pos") ?: error("MPV time-pos unavailable")

        val eventCountBeforeRecovery = trace.events.size
        trace.recordSelection("manual-B")
        instrumentation.runOnMainSync {
            // Saved progress can lag behind the native seek position.
            val episodeListManager = PlayerViewModel::class.java.getDeclaredField("episodeListManager")
                .apply { isAccessible = true }
                .get(player) as PlayerEpisodeListManager
            val currentEpisodeId = checkNotNull(player.currentEpisode.value).id
            episodeListManager.patchEpisode(currentEpisodeId) { it.copy(lastSecondSeen = 0L) }
            player.showSheet(Sheets.None)
            player.showSheet(Sheets.QualityTracks)
            player.onVideoClicked(0, 1)
        }
        trace.awaitError(missing)
        trace.awaitEvent(MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED, media, after = eventCountBeforeRecovery)
        trace.awaitEvent(MPVLib.mpvEventId.MPV_EVENT_PLAYBACK_RESTART, media, after = eventCountBeforeRecovery)
        awaitCondition("Recovery setup did not complete") { !player.isLoadingEpisode.value }
        instrumentation.runOnMainSync { }

        val recoveredPosition = MPVLib.getPropertyDouble("time-pos") ?: error("MPV time-pos unavailable after recovery")
        assertEquals("Recovery changed the saved position", retainedPosition, recoveredPosition, 0.5)
        assertEquals("Recovery changed the paused state", true, MPVLib.getPropertyBoolean("pause"))

        instrumentation.runOnMainSync { player.unpause() }
        val advancedPosition = trace.awaitPositionAfter(recoveredPosition + 0.1)
        assertTrue(
            "Playback did not advance after explicit resume: $recoveredPosition -> $advancedPosition",
            advancedPosition > recoveredPosition,
        )
    }

    @After
    fun tearDown() {
        runBlocking {
            runCatching { releaseUnloadGate() }
            MPVLib.removeObserver(trace)
            MPVLib.removeLogObserver(trace)
            try {
                scenario?.close()
            } finally {
                scenario = null
                try {
                    animeId?.let { id ->
                        val episodes = appGraph.episodeRepository.getEpisodeByAnimeId(id)
                        if (episodes.isNotEmpty()) {
                            appGraph.episodeRepository.syncEpisodes(
                                removedIds = episodes.map { it.id },
                                added = emptyList(),
                                updates = emptyList(),
                            )
                        }
                        appGraph.animeDatabaseHandler.await {
                            animesQueries.delete(id)
                        }
                    }
                } finally {
                    animeId = null
                    File(context.cacheDir, MCP_RELEASE_MARKER).delete()
                    tempFiles.forEach(File::delete)
                    tempFiles.clear()
                }
            }
        }
    }

    private suspend fun launchPlayer(videos: List<Video>) {
        val hosters = listOf(Hoster(hosterName = "R1176 local fixture", videoList = videos))
        launchPlayer(createEpisode(), hosters, startIndex = 0)
    }

    private suspend fun createEpisode(): Episode {
        val fixture = Anime.create().copy(
            source = LocalAnimeSource.ID,
            url = "r1176-native-${UUID.randomUUID()}",
            title = "R1176 native player fixture",
            initialized = true,
        )
        val id = checkNotNull(appGraph.animeRepository.insertAnime(fixture))
        animeId = id
        return addEpisode(id, 1.0)
    }

    private suspend fun addEpisode(id: Long, number: Double): Episode {
        return appGraph.episodeRepository.addAllEpisodes(
            listOf(
                Episode.create().copy(
                    animeId = id,
                    url = "r1176-native-episode-${UUID.randomUUID()}",
                    name = "R1176 native player episode",
                    episodeNumber = number,
                    totalSeconds = 24,
                ),
            ),
        ).single()
    }

    private fun launchPlayer(episode: Episode, hosters: List<Hoster>, startIndex: Int = -1) {
        val intent = PlayerActivity.newIntent(context, episode.animeId, episode.id, hosters, startIndex, startIndex)
        MPVLib.removeObserver(trace)
        MPVLib.removeLogObserver(trace)
        MPVLib.addObserver(trace)
        MPVLib.addLogObserver(trace)
        scenario = ActivityScenario.launch(intent)
    }

    private fun playerViewModel(): PlayerViewModel {
        lateinit var viewModel: PlayerViewModel
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<PlayerActivity>().single()
            viewModel = ViewModelProvider(activity)[PlayerViewModel::class.java]
        }
        return viewModel
    }

    private fun clickVideo(viewModel: PlayerViewModel, index: Int, label: String) {
        trace.recordSelection(label)
        instrumentation.runOnMainSync {
            viewModel.showSheet(Sheets.None)
            viewModel.showSheet(Sheets.QualityTracks)
            viewModel.onVideoClicked(0, index)
        }
        awaitCondition("The quality selection did not complete") {
            viewModel.dismissSheet.value || viewModel.sheetShown.value == Sheets.None
        }
        instrumentation.runOnMainSync { }
    }

    private fun video(file: File, title: String = file.name): Video = Video(
        videoUrl = file.toURI().toString(),
        videoTitle = title,
        mpvArgs = listOf("hwdec" to "no"),
        initialized = true,
    )

    private fun mediaFile(): File {
        val output = File(context.cacheDir, "r1176-${UUID.randomUUID()}.y4m")
        output.outputStream().buffered().use { stream ->
            stream.write("YUV4MPEG2 W16 H16 F25:1 Ip A1:1 C420jpeg\n".toByteArray())
            val frameHeader = "FRAME\n".toByteArray()
            val chromaPlane = ByteArray(64) { 128.toByte() }
            repeat(600) { frame ->
                val lumaPlane = ByteArray(256) { 32 }
                repeat(16) { row -> lumaPlane[row * 16 + frame % 16] = 220.toByte() }
                stream.write(frameHeader)
                stream.write(lumaPlane)
                stream.write(chromaPlane)
                stream.write(chromaPlane)
            }
        }
        tempFiles += output
        return output
    }

    private fun missingFile(label: String): File = File(context.cacheDir, "r1176-$label-${UUID.randomUUID()}.missing")

    private fun loadUnloadGate() {
        val script = File(context.cacheDir, "r1176-unload-gate-${UUID.randomUUID()}.lua")
        script.writeText(UNLOAD_GATE_LUA)
        tempFiles += script
        MPVLib.command(arrayOf("load-script", script.path))
        awaitCondition("MPV unload gate script did not register") {
            trace.logs.any { it.contains("R1176_NATIVE gate READY") }
        }
    }

    private fun releaseUnloadGate() {
        if (trace.logs.none { it.contains("R1176_NATIVE gate READY") }) return
        val held = trace.logs.count { it.contains("R1176_NATIVE gate HELD") }
        val released = trace.logs.count { it.contains("R1176_NATIVE gate RELEASED") }
        MPVLib.command(arrayOf("script-message", "r1176-release-unload"))
        if (held > released) {
            awaitCondition("MPV unload gate did not resume") {
                trace.logs.count { it.contains("R1176_NATIVE gate RELEASED") } >= held
            }
        }
    }

    private fun awaitPositionAtLeast(position: Double) {
        awaitCondition("MPV did not reach $position seconds") {
            (MPVLib.getPropertyDouble("time-pos") ?: 0.0) >= position
        }
    }

    private fun awaitPlaylistFile(video: Video) {
        val expected = Uri.parse(video.videoUrl).path.orEmpty()
        awaitCondition("MPV did not accept ${video.videoTitle} before the unload gate released") {
            MPVLib.getPropertyString("playlist/0/filename")?.sameFile(File(expected)) == true
        }
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + LOAD_TIMEOUT_MS
        while (!condition()) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            assertTrue("$message: ${trace.snapshot()}", remaining > 0)
            trace.signal.tryAcquire(minOf(remaining, CONDITION_POLL_MS), TimeUnit.MILLISECONDS)
        }
    }

    private fun holdForMcpSmoke() {
        val marker = File(context.cacheDir, MCP_RELEASE_MARKER)
        marker.delete()
        Log.i(TAG, "R1176_MCP_HOLD playbackReady releaseMarker=${marker.absolutePath}")
        val deadline = SystemClock.elapsedRealtime() + MCP_HOLD_TIMEOUT_MS
        while (!marker.exists()) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            assertTrue("Timed out while waiting for MCP smoke release marker", remaining > 0)
            trace.signal.tryAcquire(minOf(remaining, CONDITION_POLL_MS), TimeUnit.MILLISECONDS)
        }
    }

    private fun NativeTrace.awaitEvent(eventId: Int, file: File, after: Int = 0) {
        awaitCondition("Missing native ${eventName(eventId)} for ${file.name}") {
            events.drop(after).any { it.eventId == eventId && it.path?.sameFile(file) == true }
        }
    }

    private fun NativeTrace.awaitError(file: File): NativeError {
        var error: NativeError? = null
        awaitCondition("Missing native error for ${file.name}") {
            error = errors.firstOrNull { it.path?.sameFile(file) == true }
                ?: errors.singleOrNull()?.takeIf {
                    // Lua retains file identity when MPV clears its path before the native callback.
                    it.path == null && it.message.isNotEmpty() && hasErrorFor(file)
                }
            error != null
        }
        return checkNotNull(error)
    }

    private fun NativeTrace.awaitPositionAfter(value: Double): Double {
        awaitCondition("Native time-pos did not advance beyond $value") {
            (MPVLib.getPropertyDouble("time-pos") ?: 0.0) > value
        }
        return checkNotNull(MPVLib.getPropertyDouble("time-pos"))
    }

    private fun NativeTrace.hasErrorFor(file: File): Boolean =
        errors.any { it.path?.sameFile(file) == true } || logs.any {
            it.contains("R1176_NATIVE event=END_FILE") && it.contains(" reason=error ") && it.contains(file.path)
        }

    private fun NativeTrace.hasStartFor(file: File, after: Int): Boolean =
        events.drop(after).any {
            it.eventId == MPVLib.mpvEventId.MPV_EVENT_START_FILE && it.path?.sameFile(file) == true
        }

    private fun NativeTrace.hasStartFor(file: File): Boolean =
        events.any { it.eventId == MPVLib.mpvEventId.MPV_EVENT_START_FILE && it.path?.sameFile(file) == true } ||
            logs.any {
                it.contains("R1176_NATIVE event=START_FILE") && it.contains(file.path)
            }

    private fun NativeTrace.snapshot(): String =
        "events=${events.map {
            "${eventName(it.eventId)}:${it.path}"
        }} errors=${errors.map { "${it.message}:${it.path}" }} logs=${logs.toList().takeLast(15)}"

    private class NativeTrace : MPVLib.EventObserver, MPVLib.LogObserver {
        private val sequence = AtomicInteger()
        val signal = Semaphore(0)
        val events = ConcurrentLinkedQueue<NativeEvent>()
        val errors = ConcurrentLinkedQueue<NativeError>()
        val logs = ConcurrentLinkedQueue<String>()
        private var startedPath: String? = null

        fun recordSelection(label: String) {
            Log.i(TAG, "selection label=$label")
        }

        override fun eventProperty(property: String) = Unit
        override fun eventProperty(property: String, value: Long) = Unit
        override fun eventProperty(property: String, value: Boolean) = Unit
        override fun eventProperty(property: String, value: String) = Unit

        override fun eventProperty(property: String, value: Double) {
            signal.release()
        }

        override fun event(eventId: Int) {
            val path = MPVLib.getPropertyString("path")
            if (eventId == MPVLib.mpvEventId.MPV_EVENT_START_FILE) startedPath = path
            val seq = sequence.incrementAndGet()
            events += NativeEvent(seq, eventId, path)
            Log.i(TAG, "native seq=$seq event=${PlayerNativePlaybackAndroidTest.eventName(eventId)} path=$path")
            signal.release()
        }

        override fun efEvent(err: String?) {
            val path = startedPath
            val seq = sequence.incrementAndGet()
            errors += NativeError(seq, err.orEmpty(), path)
            Log.i(TAG, "native seq=$seq event=ERROR path=$path message=${err.orEmpty()}")
            signal.release()
        }

        override fun logMessage(prefix: String, level: Int, text: String) {
            logs += text
            if (text.contains("R1176_NATIVE")) Log.i(TAG, "native ${text.trim()}")
            signal.release()
        }
    }

    private data class NativeEvent(val sequence: Int, val eventId: Int, val path: String?)
    private data class NativeError(val sequence: Int, val message: String, val path: String?)

    private fun String.sameFile(file: File): Boolean =
        runCatching {
            File(Uri.parse(this).path ?: this).canonicalFile == file.canonicalFile
        }.getOrDefault(false)

    private companion object {
        const val TAG = "R1176NativePlayback"
        const val LOAD_TIMEOUT_MS = 15_000L
        const val MCP_HOLD_TIMEOUT_MS = 120_000L
        const val CONDITION_POLL_MS = 100L
        const val MCP_RELEASE_MARKER = "r1176-mcp-release"
        fun eventName(eventId: Int): String = when (eventId) {
            MPVLib.mpvEventId.MPV_EVENT_START_FILE -> "START_FILE"
            MPVLib.mpvEventId.MPV_EVENT_FILE_LOADED -> "FILE_LOADED"
            MPVLib.mpvEventId.MPV_EVENT_END_FILE -> "END_FILE"
            else -> "EVENT_$eventId"
        }
        val UNLOAD_GATE_LUA = """
            mp.msg.warn('R1176_NATIVE gate script loaded')
            local path_by_id = {}
            local function filename_for_id(id)
                for _, entry in ipairs(mp.get_property_native('playlist') or {}) do
                    if entry.id == id then return entry.filename end
                end
                return mp.get_property('path') or ''
            end
            local function trace_event(name, event)
                local id = event and event.playlist_entry_id or -1
                local path = path_by_id[id] or filename_for_id(id)
                if name == 'START_FILE' then path_by_id[id] = path end
            mp.msg.warn('R1176_NATIVE event=' .. name .. ' entry=' .. tostring(id) .. ' path=' .. path ..
                    ' reason=' .. tostring(event and event.reason or '') ..
                    ' error=' .. tostring(event and event.error or ''))
                if name == 'END_FILE' then path_by_id[id] = nil end
            end
            mp.register_event('start-file', function(event) trace_event('START_FILE', event) end)
            mp.register_event('file-loaded', function(event) trace_event('FILE_LOADED', event) end)
            mp.register_event('end-file', function(event) trace_event('END_FILE', event) end)
            local resume_unload
            local hold_unload = false
            mp.register_script_message('r1176-hold-unload', function()
                hold_unload = true
                mp.msg.warn('R1176_NATIVE gate ARMED')
            end)
            mp.add_hook('on_unload', 50, function(hook)
                if hold_unload then
                    resume_unload = hook
                    hook:defer()
                    mp.msg.warn('R1176_NATIVE gate HELD')
                end
            end)
            mp.register_script_message('r1176-release-unload', function()
                hold_unload = false
                if resume_unload then
                    local resume = resume_unload
                    resume_unload = nil
                    resume:cont()
                    mp.msg.warn('R1176_NATIVE gate RELEASED')
                end
            end)
            mp.msg.warn('R1176_NATIVE gate READY')
        """.trimIndent()
    }
}
