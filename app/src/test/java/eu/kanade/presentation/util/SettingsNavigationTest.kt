package eu.kanade.presentation.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class SettingsNavigationTest {

    @Test
    fun `entering two pane removes one pane root and keeps nested destination`() {
        settingsStackForTwoPane(
            screens = listOf("main", "appearance", "custom accent"),
            isMainScreen = { it == "main" },
            defaultScreen = "appearance",
        ) shouldBe listOf("appearance", "custom accent")
    }

    @Test
    fun `entering two pane selects appearance when one pane has no destination`() {
        settingsStackForTwoPane(
            screens = listOf("main"),
            isMainScreen = { it == "main" },
            defaultScreen = "appearance",
        ) shouldBe listOf("appearance")
    }

    @Test
    fun `entering one pane adds its root before a two pane destination`() {
        settingsStackForSinglePane(
            screens = listOf("appearance", "custom accent"),
            isMainScreen = { it == "main" },
            mainScreen = "main",
        ) shouldBe listOf("main", "appearance", "custom accent")
    }

    @Test
    fun `entering one pane keeps an existing one pane root`() {
        settingsStackForSinglePane(
            screens = listOf("main", "appearance"),
            isMainScreen = { it == "main" },
            mainScreen = "main",
        ) shouldBe listOf("main", "appearance")
    }
}
