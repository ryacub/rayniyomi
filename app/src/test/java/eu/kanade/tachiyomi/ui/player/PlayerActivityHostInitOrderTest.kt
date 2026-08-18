package eu.kanade.tachiyomi.ui.player

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.text.Charsets.UTF_8

class PlayerActivityHostInitOrderTest {

    @Test
    fun `player host view initializes before compose host and mpv setup`() {
        val source = loadPlayerActivitySource()
        val initIdx = source.indexOf(
            "playerView = LayoutInflater.from(this).inflate(R.layout.player_surface, null, false) as AniyomiMPVView",
        )
        val setContentIdx = source.indexOf("setContent {")
        val setupMpvIdx = source.indexOf("setupPlayerMPV()")

        assertTrue(initIdx >= 0, "Expected eager playerView initialization line to exist")
        assertTrue(setContentIdx >= 0, "Expected compose setContent host call to exist")
        assertTrue(setupMpvIdx >= 0, "Expected setupPlayerMPV call to exist")
        assertTrue(initIdx < setContentIdx, "playerView must initialize before setContent")
        assertTrue(initIdx < setupMpvIdx, "playerView must initialize before setupPlayerMPV")
    }

    @Test
    fun `root view assignment happens before first snackbar usage`() {
        val source = loadPlayerActivitySource()
        val rootAssignIdx = source.indexOf("rootView = findViewById(android.R.id.content)")
        val snackbarIdx = source.indexOf("Snackbar.make(")
        val systemUiVisibilityIdx = source.indexOf("rootView.systemUiVisibility")

        assertTrue(rootAssignIdx >= 0, "Expected rootView assignment to exist")
        assertTrue(snackbarIdx >= 0, "Expected Snackbar usage to exist")
        assertTrue(systemUiVisibilityIdx >= 0, "Expected rootView.systemUiVisibility usage to exist")
        assertTrue(rootAssignIdx < snackbarIdx, "rootView assignment must happen before Snackbar usage")
        assertTrue(
            rootAssignIdx < systemUiVisibilityIdx,
            "rootView assignment must happen before rootView.systemUiVisibility usage",
        )
    }

    @Test
    fun `player activity does not replace the process uncaught exception handler`() {
        val source = loadPlayerActivitySource()

        assertTrue(
            !source.contains("Thread.setDefaultUncaughtExceptionHandler"),
            "PlayerActivity must not replace process-wide crash handling",
        )
        assertTrue(
            !source.contains("Thread.getDefaultUncaughtExceptionHandler"),
            "PlayerActivity must not read process-wide crash handling",
        )
    }

    @Test
    fun `mpv setup failures route through the local initial episode error path`() {
        val source = loadPlayerActivitySource()
        val setupMpvIdx = source.indexOf("private suspend fun setupPlayerMPV(): Boolean")
        val catchIdx = source.indexOf("catch (error: Exception)", startIndex = setupMpvIdx)
        val errorPathIdx = source.indexOf("setInitialEpisodeError(error)", startIndex = catchIdx)
        val falseResultIdx = source.indexOf("return false", startIndex = errorPathIdx)

        assertTrue(setupMpvIdx >= 0, "Expected setupPlayerMPV to exist")
        assertTrue(catchIdx > setupMpvIdx, "Expected setupPlayerMPV to catch startup failures locally")
        assertTrue(errorPathIdx > catchIdx, "Expected MPV startup failures to use setInitialEpisodeError")
        assertTrue(falseResultIdx > errorPathIdx, "Expected MPV startup failures to stop player startup")
    }

    @Test
    fun `player view model routes selected activity calls through player host`() {
        val source = loadPlayerViewModelSource()

        assertTrue(source.contains("private val host: PlayerHost"))
        assertTrue(source.contains("host.setVideo(video)"))
        assertTrue(source.contains("host.showToast(message)"))
        assertTrue(source.contains("host.setupCustomButtons(buttons)"))
        assertTrue(source.contains("host.changeEpisode("))
        assertTrue(!source.contains("activity.setVideo("))
        assertTrue(!source.contains("activity.showToast("))
        assertTrue(!source.contains("activity.setupCustomButtons("))
        assertTrue(!source.contains("activity.changeEpisode("))
    }

    /**
     * The player makes many MPVLib calls that assume a live handle. File preparation can stay on
     * IO, but the remaining player startup cannot run until mpv is initialized.
     */
    @Test
    fun `player startup continues only after mpv setup succeeds`() {
        val source = loadPlayerActivitySource()
        val onCreateIdx = source.indexOf("override fun onCreate")
        val startupIdx = source.indexOf("private fun startPlayerAfterMpvReady()")

        assertTrue(onCreateIdx >= 0, "Expected onCreate to exist")
        assertTrue(startupIdx > onCreateIdx, "Expected deferred startup helper to exist after onCreate")

        val onCreateBody = source.substring(onCreateIdx, startupIdx)
        val setupIdx = onCreateBody.indexOf("if (!setupPlayerMPV())")
        val returnIdx = onCreateBody.indexOf("return@launch", startIndex = setupIdx)
        val continueIdx = onCreateBody.indexOf("startPlayerAfterMpvReady()", startIndex = returnIdx)

        assertTrue(setupIdx >= 0, "onCreate must branch on MPV setup failure")
        assertTrue(returnIdx > setupIdx, "onCreate must stop startup when MPV setup fails")
        assertTrue(
            continueIdx > returnIdx,
            "player setup and initial intent handling must wait until MPV setup succeeds",
        )
    }

    @Test
    fun `mpv file preparation stays on io and is awaited before player initialize`() {
        val initializerSource = loadPlayerSource("PlayerMpvInitializer.kt")
        val initIdx = initializerSource.indexOf("suspend fun initialize(")
        val initBody = initializerSource.substring(initIdx, initializerSource.indexOf("private fun copyUserFiles"))

        assertTrue(initIdx >= 0, "Expected PlayerMpvInitializer.initialize to exist")
        assertTrue(initBody.contains("withContext(Dispatchers.IO)"), "file preparation must stay off the main thread")

        val activitySource = loadPlayerActivitySource()
        val setupIdx = activitySource.indexOf("private suspend fun setupPlayerMPV()")
        val setupBody = activitySource.substring(setupIdx, activitySource.indexOf("fun setupCustomButtons"))
        val prepareIdx = setupBody.indexOf("mpvInitializer.initialize(")
        val playerInitIdx = setupBody.indexOf("player.initialize(")

        assertTrue(setupIdx >= 0, "Expected suspend setupPlayerMPV to exist")
        assertTrue(prepareIdx >= 0, "Expected MPV file preparation in setupPlayerMPV")
        assertTrue(playerInitIdx > prepareIdx, "player.initialize must run only after file preparation returns")
        assertTrue(
            !setupBody.contains("launchIO") && !setupBody.contains("withUIContext"),
            "setupPlayerMPV must await preparation directly instead of starting another player race",
        )
    }

    @Test
    fun `setup player mpv does not call mpv lib before player initialize`() {
        val activitySource = loadPlayerActivitySource()
        val setupIdx = activitySource.indexOf("private suspend fun setupPlayerMPV()")
        val setupBody = activitySource.substring(setupIdx, activitySource.indexOf("fun setupCustomButtons"))
        val playerInitIdx = setupBody.indexOf("player.initialize(")
        val preInitBody = setupBody.substring(0, playerInitIdx)

        assertTrue(setupIdx >= 0, "Expected setupPlayerMPV to exist")
        assertTrue(playerInitIdx >= 0, "Expected player.initialize to exist")
        assertTrue(
            !preInitBody.contains("MPVLib."),
            "setupPlayerMPV must not call MPVLib before player.initialize creates the mpv handle",
        )
    }

    @Test
    fun `key events do not touch player state before mpv is ready`() {
        val source = loadPlayerActivitySource()
        val keyDownBody = source.functionBody("override fun onKeyDown")
        val keyUpBody = source.functionBody("override fun onKeyUp")

        assertTrue(
            keyDownBody.contains("if (!mpvReady)") &&
                keyDownBody.indexOf("return super.onKeyDown") < keyDownBody.indexOf("viewModel.changeVolumeBy"),
            "onKeyDown must return before ViewModel or MPV access when mpv is not ready",
        )
        assertTrue(
            keyUpBody.contains("if (!mpvReady || event == null)") &&
                keyUpBody.indexOf("return super.onKeyUp") < keyUpBody.indexOf("player.onKey(event)"),
            "onKeyUp must return before player key handling when mpv is not ready",
        )
    }

    @Test
    fun `custom button script setup waits until mpv is ready`() {
        val source = loadPlayerActivitySource()
        val setupBody = source.functionBody("fun setupCustomButtons")

        assertTrue(setupBody.contains("pendingCustomButtons = buttons"), "buttons must be queued before mpv is ready")
        assertTrue(
            setupBody.indexOf("if (!mpvReady)") < setupBody.indexOf("viewModel.primaryButton"),
            "setupCustomButtons must not create the ViewModel before mpv is ready",
        )
    }

    @Test
    fun `stop and save state do not create view model before mpv is ready`() {
        val source = loadPlayerActivitySource()
        val stopBody = source.functionBody("override fun onStop")
        val saveBody = source.functionBody("override fun onSaveInstanceState")

        assertTrue(
            stopBody.contains("if (!mpvReady)") &&
                stopBody.indexOf("return") < stopBody.indexOf("playerPreferences"),
            "onStop must return before preference access creates the ViewModel",
        )
        assertTrue(
            saveBody.contains("if (mpvReady && !isChangingConfigurations)"),
            "onSaveInstanceState must not create the ViewModel before mpv is ready",
        )
    }

    @Test
    fun `player view model construction does not read mpv state`() {
        val source = loadPlayerViewModelSource()
        val constructorIdx = source.indexOf("class PlayerViewModel")
        val stateInitEnd = source.indexOf("// Pair(startingPosition, seekAmount)")
        val propertyInitializers = source.substring(constructorIdx, stateInitEnd)

        assertTrue(constructorIdx >= 0, "Expected PlayerViewModel to exist")
        assertTrue(stateInitEnd > constructorIdx, "Expected player state initializer block to exist")
        assertTrue(
            !propertyInitializers.contains("MPVLib.getPropertyString(\"hwdec\")"),
            "PlayerViewModel construction must not read hwdec from MPVLib",
        )
        assertTrue(
            !propertyInitializers.contains("MPVLib.getPropertyInt(\"volume\")"),
            "PlayerViewModel construction must not read volume from MPVLib",
        )
        assertTrue(
            !propertyInitializers.contains("MPVLib.getPropertyInt(\"volume-max\")"),
            "PlayerViewModel construction must not read volume-max from MPVLib",
        )
    }

    /**
     * The activity handles orientation and window-size configuration changes itself, so a
     * bounds change delivered while the player is backgrounded (for example, exiting
     * split-screen) never re-measures the player view. onResume() itself runs in the same
     * transition frame as onConfigurationChanged/onStart, before Compose has recomposed the
     * player view against the new window bounds, so the resume path must wait for a real
     * layout pass that reports the current window width before forcing the surface to
     * recreate.
     */
    @Test
    fun `resume registers a layout listener to re-measure the player view after a backgrounded window resize`() {
        val resumeBody = loadPlayerActivitySource().functionBody("override fun onResume")
        val isExitingClearIdx = resumeBody.indexOf("player.isExiting = false")
        val resumeSuperIdx = resumeBody.indexOf("super.onResume()", startIndex = isExitingClearIdx)
        val addListenerIdx = resumeBody.indexOf("playerView.viewTreeObserver.addOnGlobalLayoutListener")
        val forceLayoutIdx = resumeBody.indexOf("playerView.forceLayout()")
        val requestLayoutIdx = resumeBody.indexOf("playerView.requestLayout()")
        val volumeIdx = resumeBody.indexOf("viewModel.currentVolume.update")

        assertTrue(isExitingClearIdx >= 0, "Expected onResume to clear player.isExiting")
        assertTrue(resumeSuperIdx >= 0, "Expected resume to continue after clearing player.isExiting")
        assertTrue(
            addListenerIdx > resumeSuperIdx,
            "Layout listener must be registered only when returning from background",
        )
        assertTrue(
            forceLayoutIdx > addListenerIdx,
            "Layout pass must run inside the layout listener, not synchronously in onResume",
        )
        assertTrue(
            requestLayoutIdx > forceLayoutIdx,
            "Layout pass must force a measure and layout request",
        )
        assertTrue(
            addListenerIdx < volumeIdx,
            "Layout listener registration must stay grouped with the returning-from-background work",
        )
    }

    @Test
    fun `resume does not register a layout listener on a normal resume`() {
        val resumeBody = loadPlayerActivitySource().functionBody("override fun onResume")
        val notExitingGuardIdx = resumeBody.indexOf("if (!player.isExiting)")
        val guardReturnIdx = resumeBody.indexOf("return", startIndex = notExitingGuardIdx)
        val addListenerIdx = resumeBody.indexOf("playerView.viewTreeObserver.addOnGlobalLayoutListener")

        assertTrue(notExitingGuardIdx >= 0, "Expected onResume to guard on player.isExiting")
        assertTrue(guardReturnIdx > notExitingGuardIdx, "Expected the not-exiting branch to return early")
        assertTrue(
            guardReturnIdx < addListenerIdx,
            "Layout listener must not be registered on a normal resume",
        )
    }

    /**
     * The layout pass alone does not make mpv reattach its surface: Android redelivers
     * SurfaceHolder.Callback#surfaceCreated only when the SurfaceView's own visibility
     * changes, not merely when the window becomes visible again at new bounds. The resume
     * path must toggle the player view's visibility, posted after the layout pass reports
     * the current window width inside the listener fires.
     */
    @Test
    fun `resume toggles player view visibility only after the layout listener reports the current size`() {
        val resumeBody = loadPlayerActivitySource().functionBody("override fun onResume")
        val addListenerIdx = resumeBody.indexOf("playerView.viewTreeObserver.addOnGlobalLayoutListener")
        val forceLayoutIdx = resumeBody.indexOf("playerView.forceLayout()")
        val postIdx = resumeBody.indexOf("playerView.post")
        val goneIdx = resumeBody.indexOf("playerView.visibility = View.GONE")
        val visibleIdx = resumeBody.indexOf("playerView.visibility = View.VISIBLE")
        val volumeIdx = resumeBody.indexOf("viewModel.currentVolume.update")

        assertTrue(addListenerIdx >= 0, "Expected onResume to register an on-global-layout listener")
        assertTrue(forceLayoutIdx > addListenerIdx, "Layout pass must run inside the layout listener")
        assertTrue(postIdx > addListenerIdx, "Visibility toggle must run inside the layout listener")
        assertTrue(postIdx > forceLayoutIdx, "Visibility toggle must be posted after the layout pass")
        assertTrue(goneIdx > postIdx, "Expected a posted visibility toggle to GONE")
        assertTrue(visibleIdx > goneIdx, "Expected the visibility toggle to restore VISIBLE")
        assertTrue(
            visibleIdx < volumeIdx,
            "Visibility toggle must stay grouped with the returning-from-background work",
        )
    }

    private fun loadPlayerActivitySource(): String {
        return loadPlayerSource("PlayerActivity.kt")
    }

    private fun loadPlayerViewModelSource(): String {
        return loadPlayerSource("PlayerViewModel.kt")
    }

    private fun loadPlayerSource(fileName: String): String {
        val moduleRelative = Paths.get(
            "src/main/java/eu/kanade/tachiyomi/ui/player/$fileName",
        )
        val rootRelative = Paths.get(
            "app/src/main/java/eu/kanade/tachiyomi/ui/player/$fileName",
        )
        val sourcePath = when {
            Files.exists(moduleRelative) -> moduleRelative
            Files.exists(rootRelative) -> rootRelative
            else -> throw java.nio.file.NoSuchFileException(
                "Could not find $fileName from module or repo root paths",
            )
        }
        return String(Files.readAllBytes(sourcePath), UTF_8)
    }

    private fun String.functionBody(signature: String): String {
        val start = indexOf(signature)
        assertTrue(start >= 0, "Expected function signature to exist: $signature")

        val bodyStart = indexOf("{", startIndex = start)
        assertTrue(bodyStart >= 0, "Expected function body to exist: $signature")

        var depth = 0
        for (index in bodyStart until length) {
            when (this[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return substring(bodyStart, index + 1)
                }
            }
        }
        throw IllegalArgumentException("Function body not closed: $signature")
    }
}
