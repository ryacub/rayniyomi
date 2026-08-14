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
        val setupMpvIdx = source.indexOf("private fun setupPlayerMPV()")
        val catchIdx = source.indexOf("catch (error: Exception)", startIndex = setupMpvIdx)
        val errorPathIdx = source.indexOf("setInitialEpisodeError(error)", startIndex = catchIdx)

        assertTrue(setupMpvIdx >= 0, "Expected setupPlayerMPV to exist")
        assertTrue(catchIdx > setupMpvIdx, "Expected setupPlayerMPV to catch startup failures locally")
        assertTrue(errorPathIdx > catchIdx, "Expected MPV startup failures to use setInitialEpisodeError")
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
     * The player makes ~190 MPVLib calls and none of them check that mpv is running. That is only
     * safe while mpv starts inline in onCreate, which is what upstream aniyomi does. Moving the
     * startup onto a background scope reopens a window in which onNewIntent,
     * onConfigurationChanged or composition reaches mpv first, and the process aborts on a null
     * jmethodID rather than reporting anything useful.
     */
    @Test
    fun `mpv starts inline in onCreate rather than on a background scope`() {
        val source = loadPlayerActivitySource()
        val setupIdx = source.indexOf("private fun setupPlayerMPV()")
        assertTrue(setupIdx >= 0, "Expected setupPlayerMPV to exist")

        // Stop at the next declaration, whether or not it is private, so a neighbouring function
        // that legitimately uses a coroutine scope cannot be read as part of this one.
        val bodyEnd = listOf("\n    private fun ", "\n    fun ")
            .mapNotNull { source.indexOf(it, startIndex = setupIdx + 1).takeIf { i -> i >= 0 } }
            .min()
        val body = source.substring(setupIdx, bodyEnd)

        assertTrue(body.contains("player.initialize("), "Expected setupPlayerMPV to initialize the player")
        assertTrue(
            !body.contains("launchIO") && !body.contains("lifecycleScope") && !body.contains("withUIContext"),
            "setupPlayerMPV must start mpv inline; a background scope lets callers reach mpv first",
        )
    }

    @Test
    fun `mpv file preparation stays blocking so the caller cannot outrun it`() {
        val source = loadPlayerSource("PlayerMpvInitializer.kt")
        val initIdx = source.indexOf("fun initialize(")
        val body = source.substring(initIdx, source.indexOf("private fun copyUserFiles"))

        assertTrue(initIdx >= 0, "Expected PlayerMpvInitializer.initialize to exist")
        assertTrue(
            !source.substring(0, initIdx).endsWith("suspend "),
            "initialize must not be suspend; onCreate cannot await it",
        )
        assertTrue(
            !body.contains("withContext"),
            "initialize must not hand its work to another dispatcher",
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
}
