package eu.kanade.tachiyomi.ui.more

import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.domain.update.UpdatePromptGatekeeper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

/**
 * Unit tests for NewUpdateScreen navigation callback logic.
 *
 * Tests use @VisibleForTesting extraction to verify:
 * 1. buildOnSkipVersion calls gatekeeper.skipVersion(versionName) with correct version
 * 2. buildOnSkipVersion calls navigator.pop()
 * 3. buildOnRejectUpdate calls navigator.pop()
 */
class NewUpdateScreenTest {

    // ============================================================================
    // TEST 1: buildOnSkipVersion calls gatekeeper.skipVersion with constructor versionName
    // ============================================================================

    @Test
    fun `buildOnSkipVersion calls gatekeeper skipVersion with versionName`() {
        val mockGatekeeper = mockk<UpdatePromptGatekeeper>()
        every { mockGatekeeper.skipVersion(any()) } returns Unit
        val mockNavigator = mockk<Navigator>(relaxed = true)

        val screen = NewUpdateScreen(
            versionName = "1.2.3",
            changelogInfo = "changelog",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/download",
        )
        screen.gatekeeper = mockGatekeeper

        screen.buildOnSkipVersion(mockNavigator).invoke()

        verify { mockGatekeeper.skipVersion("1.2.3") }
    }

    // ============================================================================
    // TEST 2: buildOnSkipVersion calls navigator.pop()
    // ============================================================================

    @Test
    fun `buildOnSkipVersion calls navigator pop`() {
        val mockGatekeeper = mockk<UpdatePromptGatekeeper>(relaxed = true)
        val mockNavigator = mockk<Navigator>(relaxed = true)

        val screen = NewUpdateScreen(
            versionName = "2.0.0",
            changelogInfo = "changelog",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/download",
        )
        screen.gatekeeper = mockGatekeeper

        screen.buildOnSkipVersion(mockNavigator).invoke()

        verify { mockNavigator.pop() }
    }

    // ============================================================================
    // TEST 3: versionName from constructor is used (not hardcoded)
    // ============================================================================

    @Test
    fun `buildOnSkipVersion passes versionName from constructor to gatekeeper`() {
        val mockGatekeeper = mockk<UpdatePromptGatekeeper>()
        every { mockGatekeeper.skipVersion(any()) } returns Unit
        val mockNavigator = mockk<Navigator>(relaxed = true)

        val expectedVersion = "v0.18.1.120"
        val screen = NewUpdateScreen(
            versionName = expectedVersion,
            changelogInfo = "changelog",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/download",
        )
        screen.gatekeeper = mockGatekeeper

        screen.buildOnSkipVersion(mockNavigator).invoke()

        verify(exactly = 1) { mockGatekeeper.skipVersion(expectedVersion) }
    }

    // ============================================================================
    // TEST 4: buildOnRejectUpdate calls navigator.pop()
    // ============================================================================

    @Test
    fun `buildOnRejectUpdate calls navigator pop`() {
        val mockNavigator = mockk<Navigator>(relaxed = true)

        val screen = NewUpdateScreen(
            versionName = "1.0.0",
            changelogInfo = "changelog",
            releaseLink = "https://example.com",
            downloadLink = "https://example.com/download",
        )

        screen.buildOnRejectUpdate(mockNavigator).invoke()

        verify { mockNavigator.pop() }
    }
}
