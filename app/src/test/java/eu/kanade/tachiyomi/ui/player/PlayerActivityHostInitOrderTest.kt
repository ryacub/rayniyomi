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

        assertTrue(rootAssignIdx >= 0, "Expected rootView assignment to exist")
        assertTrue(snackbarIdx >= 0, "Expected Snackbar usage to exist")
        assertTrue(rootAssignIdx < snackbarIdx, "rootView assignment must happen before Snackbar usage")
    }

    private fun loadPlayerActivitySource(): String {
        val moduleRelative = Paths.get(
            "src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt",
        )
        val rootRelative = Paths.get(
            "app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt",
        )
        val sourcePath = when {
            Files.exists(moduleRelative) -> moduleRelative
            Files.exists(rootRelative) -> rootRelative
            else -> throw java.nio.file.NoSuchFileException(
                "Could not find PlayerActivity.kt from module or repo root paths",
            )
        }
        return String(Files.readAllBytes(sourcePath), UTF_8)
    }
}
