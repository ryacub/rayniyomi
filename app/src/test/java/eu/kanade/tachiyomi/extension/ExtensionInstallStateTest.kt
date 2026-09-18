package eu.kanade.tachiyomi.extension

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class ExtensionInstallStateTest {

    @Test
    fun `completed errors remain until dismissed and successful installs clear only their state`() {
        var state = emptyMap<String, InstallStep>()

        state = state.withInstallStep("manga.one", InstallStep.Error)
        state = state.withInstallStep("anime.one", InstallStep.Error)
        state = state.completeInstall("manga.one")

        state["manga.one"] shouldBe InstallStep.Error
        state["anime.one"] shouldBe InstallStep.Error

        state = state.dismissInstallError("manga.one")
        state["manga.one"] shouldBe null
        state["anime.one"] shouldBe InstallStep.Error

        state = state.withInstallStep("manga.two", InstallStep.Downloading)
        state = state.dismissInstallError("manga.two")
        state["manga.two"] shouldBe InstallStep.Downloading

        state = state.withInstallStep("anime.one", InstallStep.Installed)
        state = state.completeInstall("anime.one")

        state["anime.one"] shouldBe null
    }
}
