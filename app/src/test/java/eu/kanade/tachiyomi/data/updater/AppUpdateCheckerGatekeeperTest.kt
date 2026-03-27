package eu.kanade.tachiyomi.data.updater

import android.content.Context
import eu.kanade.domain.update.UpdatePromptGatekeeper
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.domain.release.interactor.GetApplicationRelease
import tachiyomi.domain.release.model.Release

/**
 * RED tests for AppUpdateChecker gatekeeper integration.
 *
 * Tests verify that AppUpdateChecker uses UpdatePromptGatekeeper to:
 * 1. Gate NewUpdate result based on shouldPrompt()
 * 2. Return UpdateSuppressed when gatekeeper suppresses
 * 3. Call recordPrompted() only when prompting
 * 4. Call clearSkipIfOutdated() before checking shouldPrompt()
 * 5. Bypass gatekeeper when forceCheck=true
 *
 * Uses @VisibleForTesting mutable fields for dependency injection in tests.
 */
class AppUpdateCheckerGatekeeperTest {

    private lateinit var checker: AppUpdateChecker
    private lateinit var mockGetApplicationRelease: GetApplicationRelease
    private lateinit var mockGatekeeper: UpdatePromptGatekeeper
    private lateinit var mockContext: Context
    private lateinit var decisionEvents: MutableList<AppUpdateChecker.DecisionReason>

    @BeforeEach
    fun setUp() {
        mockGetApplicationRelease = mockk()
        mockGatekeeper = mockk()
        mockContext = mockk(relaxed = true)
        decisionEvents = mutableListOf()
        checker = AppUpdateChecker()
        checker.getApplicationRelease = mockGetApplicationRelease
        checker.gatekeeper = mockGatekeeper
        checker.decisionLogger = { decisionEvents.add(it) }
    }

    // ============================================================================
    // TEST 1: checkForUpdate returns UpdateSuppressed when gatekeeper shouldPrompt is false
    // ============================================================================

    @Test
    fun `checkForUpdate returns UpdateSuppressed when gatekeeper shouldPrompt is false`() = runTest {
        // Arrange
        val release = mockk<Release> {
            every { version } returns "1.2.3"
        }
        val releaseVersion = "1.2.3"

        coEvery {
            mockGetApplicationRelease.await(any())
        } returns GetApplicationRelease.Result.NewUpdate(release)

        every { mockGatekeeper.clearSkipIfOutdated(releaseVersion) } returns Unit
        every { mockGatekeeper.shouldPrompt(releaseVersion) } returns false

        // Act
        val result = checker.checkForUpdate(mockContext, forceCheck = false)

        // Assert (will fail until UpdateSuppressed result type exists)
        assertEquals(GetApplicationRelease.Result.UpdateSuppressed(release), result)
        assertEquals(listOf(AppUpdateChecker.DecisionReason.SUPPRESSED_BY_GATEKEEPER), decisionEvents)
    }

    // ============================================================================
    // TEST 2: checkForUpdate returns NewUpdate when gatekeeper shouldPrompt is true
    // ============================================================================

    @Test
    fun `checkForUpdate returns NewUpdate when gatekeeper shouldPrompt is true`() = runTest {
        // Arrange
        val release = mockk<Release> {
            every { version } returns "1.5.0"
        }
        val releaseVersion = "1.5.0"

        coEvery {
            mockGetApplicationRelease.await(any())
        } returns GetApplicationRelease.Result.NewUpdate(release)

        every { mockGatekeeper.clearSkipIfOutdated(releaseVersion) } returns Unit
        every { mockGatekeeper.shouldPrompt(releaseVersion) } returns true
        every { mockGatekeeper.recordPrompted() } returns Unit

        // Act
        val result = checker.checkForUpdate(mockContext, forceCheck = false)

        // Assert
        assertEquals(GetApplicationRelease.Result.NewUpdate(release), result)
        assertEquals(listOf(AppUpdateChecker.DecisionReason.PROMPT_ALLOWED), decisionEvents)
    }

    // ============================================================================
    // TEST 3: checkForUpdate calls recordPrompted when prompting
    // ============================================================================

    @Test
    fun `checkForUpdate calls recordPrompted when prompting`() = runTest {
        // Arrange
        val release = mockk<Release> {
            every { version } returns "2.0.0"
        }
        val releaseVersion = "2.0.0"

        coEvery {
            mockGetApplicationRelease.await(any())
        } returns GetApplicationRelease.Result.NewUpdate(release)

        every { mockGatekeeper.clearSkipIfOutdated(releaseVersion) } returns Unit
        every { mockGatekeeper.shouldPrompt(releaseVersion) } returns true
        every { mockGatekeeper.recordPrompted() } returns Unit

        // Act
        checker.checkForUpdate(mockContext, forceCheck = false)

        // Assert
        verify {
            mockGatekeeper.recordPrompted()
        }
    }

    // ============================================================================
    // TEST 4: checkForUpdate does NOT call recordPrompted when suppressed
    // ============================================================================

    @Test
    fun `checkForUpdate does NOT call recordPrompted when suppressed`() = runTest {
        // Arrange
        val release = mockk<Release> {
            every { version } returns "3.0.0"
        }
        val releaseVersion = "3.0.0"

        coEvery {
            mockGetApplicationRelease.await(any())
        } returns GetApplicationRelease.Result.NewUpdate(release)

        every { mockGatekeeper.clearSkipIfOutdated(releaseVersion) } returns Unit
        every { mockGatekeeper.shouldPrompt(releaseVersion) } returns false

        // Act
        checker.checkForUpdate(mockContext, forceCheck = false)

        // Assert
        verify(exactly = 0) {
            mockGatekeeper.recordPrompted()
        }
    }

    // ============================================================================
    // TEST 5: checkForUpdate calls clearSkipIfOutdated before shouldPrompt
    // ============================================================================

    @Test
    fun `checkForUpdate calls clearSkipIfOutdated before shouldPrompt`() = runTest {
        // Arrange
        val release = mockk<Release> {
            every { version } returns "1.7.0"
        }
        val releaseVersion = "1.7.0"

        coEvery {
            mockGetApplicationRelease.await(any())
        } returns GetApplicationRelease.Result.NewUpdate(release)

        val callOrder = mutableListOf<String>()
        every { mockGatekeeper.clearSkipIfOutdated(releaseVersion) } answers {
            callOrder.add("clearSkipIfOutdated")
        }
        every { mockGatekeeper.shouldPrompt(releaseVersion) } answers {
            callOrder.add("shouldPrompt")
            false
        }

        // Act
        checker.checkForUpdate(mockContext, forceCheck = false)

        // Assert
        assertEquals(listOf("clearSkipIfOutdated", "shouldPrompt"), callOrder)
    }

    // ============================================================================
    // TEST 6: checkForUpdate with forceCheck=true returns NewUpdate even when shouldPrompt is false
    // ============================================================================

    @Test
    fun `checkForUpdate with forceCheck=true returns NewUpdate even when shouldPrompt is false`() = runTest {
        // Arrange
        val release = mockk<Release> {
            every { version } returns "2.5.0"
        }

        coEvery {
            mockGetApplicationRelease.await(any())
        } returns GetApplicationRelease.Result.NewUpdate(release)

        every { mockGatekeeper.clearSkipIfOutdated("2.5.0") } returns Unit
        every { mockGatekeeper.recordPrompted() } returns Unit

        // Act
        val result = checker.checkForUpdate(mockContext, forceCheck = true)

        // Assert
        assertEquals(GetApplicationRelease.Result.NewUpdate(release), result)
        assertEquals(listOf(AppUpdateChecker.DecisionReason.PROMPT_FORCED), decisionEvents)
    }

    // ============================================================================
    // TEST 7: checkForUpdate returns NoNewUpdate when no update available
    // ============================================================================

    @Test
    fun `checkForUpdate returns NoNewUpdate when no update available`() = runTest {
        // Arrange
        coEvery {
            mockGetApplicationRelease.await(any())
        } returns GetApplicationRelease.Result.NoNewUpdate

        // Act
        val result = checker.checkForUpdate(mockContext, forceCheck = false)

        // Assert
        assertEquals(GetApplicationRelease.Result.NoNewUpdate, result)
        assertEquals(listOf(AppUpdateChecker.DecisionReason.NO_NEW_UPDATE), decisionEvents)
    }

    @Test
    fun `checkForUpdate returns UpdateSuppressed when release version is blank`() = runTest {
        // Arrange
        val release = mockk<Release> {
            every { version } returns " "
        }

        coEvery {
            mockGetApplicationRelease.await(any())
        } returns GetApplicationRelease.Result.NewUpdate(release)

        // Act
        val result = checker.checkForUpdate(mockContext, forceCheck = false)

        // Assert
        assertEquals(GetApplicationRelease.Result.UpdateSuppressed(release), result)
        verify(exactly = 0) {
            mockGatekeeper.clearSkipIfOutdated(any())
            mockGatekeeper.shouldPrompt(any())
            mockGatekeeper.recordPrompted()
        }
        assertEquals(listOf(AppUpdateChecker.DecisionReason.SUPPRESSED_INVALID_RELEASE_VERSION), decisionEvents)
    }
}
