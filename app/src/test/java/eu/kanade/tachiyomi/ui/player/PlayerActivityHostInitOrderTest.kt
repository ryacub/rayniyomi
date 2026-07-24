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
