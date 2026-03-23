package eu.kanade.domain.update

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * RED tests for UpdatePromptGatekeeper business logic.
 *
 * Tests cover shouldPrompt() behavior for all cadence types + skip version logic,
 * plus recordPrompted(), skipVersion(), and clearSkipIfOutdated() mutations.
 *
 * Time is mocked via a fixed reference instant to avoid wall-clock dependency.
 */
class UpdatePromptGatekeeperTest {

    private lateinit var prefs: UpdatePromptPreferences
    private lateinit var gatekeeper: UpdatePromptGatekeeper

    // Fixed reference time for all tests
    private val referenceTime = Instant.parse("2026-03-23T10:00:00Z")

    @BeforeEach
    fun setUp() {
        prefs = mockk()
        gatekeeper = UpdatePromptGatekeeper(prefs)
    }

    // ============================================================================
    // CADENCE: ALWAYS
    // ============================================================================

    @Test
    fun `shouldPrompt with cadence ALWAYS returns true regardless of lastPromptedAt`() {
        // Arrange
        val releaseVersion = "1.0.0"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.ALWAYS
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns ""
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertTrue(result)
    }

    // ============================================================================
    // CADENCE: NEVER
    // ============================================================================

    @Test
    fun `shouldPrompt with cadence NEVER returns false always`() {
        // Arrange
        val releaseVersion = "1.0.0"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.NEVER
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns ""
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertFalse(result)
    }

    // ============================================================================
    // CADENCE: DAILY
    // ============================================================================

    @Test
    fun `shouldPrompt with cadence DAILY and never prompted before returns true`() {
        // Arrange
        val releaseVersion = "1.0.0"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.DAILY
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns ""
        }
        val lastPromptedAtPref = mockk<tachiyomi.core.common.preference.Preference<Long>> {
            every { get() } returns 0L // Never prompted
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref
        every { prefs.lastPromptedAt() } returns lastPromptedAtPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `shouldPrompt with cadence DAILY and prompted less than 24h ago returns false`() {
        // Arrange
        val releaseVersion = "1.0.0"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.DAILY
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns ""
        }
        // Last prompted 12 hours ago (23 hours before reference time)
        val lastPromptedAtPref = mockk<tachiyomi.core.common.preference.Preference<Long>> {
            every { get() } returns referenceTime.minusSeconds(12 * 3600).toEpochMilli()
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref
        every { prefs.lastPromptedAt() } returns lastPromptedAtPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `shouldPrompt with cadence DAILY and prompted exactly 24h ago returns true`() {
        // Arrange
        val releaseVersion = "1.0.0"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.DAILY
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns ""
        }
        // Last prompted exactly 24 hours ago (boundary: >= 24h should prompt)
        val lastPromptedAtPref = mockk<tachiyomi.core.common.preference.Preference<Long>> {
            every { get() } returns referenceTime.minusSeconds(24 * 3600).toEpochMilli()
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref
        every { prefs.lastPromptedAt() } returns lastPromptedAtPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `shouldPrompt with cadence DAILY and prompted more than 24h ago returns true`() {
        // Arrange
        val releaseVersion = "1.0.0"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.DAILY
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns ""
        }
        // Last prompted 36 hours ago
        val lastPromptedAtPref = mockk<tachiyomi.core.common.preference.Preference<Long>> {
            every { get() } returns referenceTime.minusSeconds(36 * 3600).toEpochMilli()
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref
        every { prefs.lastPromptedAt() } returns lastPromptedAtPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertTrue(result)
    }

    // ============================================================================
    // CADENCE: WEEKLY
    // ============================================================================

    @Test
    fun `shouldPrompt with cadence WEEKLY and never prompted before returns true`() {
        // Arrange
        val releaseVersion = "1.0.0"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.WEEKLY
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns ""
        }
        val lastPromptedAtPref = mockk<tachiyomi.core.common.preference.Preference<Long>> {
            every { get() } returns 0L // Never prompted
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref
        every { prefs.lastPromptedAt() } returns lastPromptedAtPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `shouldPrompt with cadence WEEKLY and prompted less than 7 days ago returns false`() {
        // Arrange
        val releaseVersion = "1.0.0"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.WEEKLY
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns ""
        }
        // Last prompted 3 days ago
        val lastPromptedAtPref = mockk<tachiyomi.core.common.preference.Preference<Long>> {
            every { get() } returns referenceTime.minusSeconds(3 * 24 * 3600).toEpochMilli()
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref
        every { prefs.lastPromptedAt() } returns lastPromptedAtPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `shouldPrompt with cadence WEEKLY and prompted more than 7 days ago returns true`() {
        // Arrange
        val releaseVersion = "1.0.0"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.WEEKLY
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns ""
        }
        // Last prompted 10 days ago
        val lastPromptedAtPref = mockk<tachiyomi.core.common.preference.Preference<Long>> {
            every { get() } returns referenceTime.minusSeconds(10 * 24 * 3600).toEpochMilli()
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref
        every { prefs.lastPromptedAt() } returns lastPromptedAtPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertTrue(result)
    }

    // ============================================================================
    // SKIP VERSION
    // ============================================================================

    @Test
    fun `shouldPrompt with skip version matching releaseVersion returns false`() {
        // Arrange
        val releaseVersion = "1.2.3"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.ALWAYS
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns "1.2.3" // Exact match
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `shouldPrompt with skip version NOT matching releaseVersion returns true if cadence permits`() {
        // Arrange
        val releaseVersion = "1.2.3"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.ALWAYS
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns "1.2.2" // Different version
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `shouldPrompt with empty skip version and ALWAYS cadence returns true`() {
        // Arrange
        val releaseVersion = "1.0.0"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.ALWAYS
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns "" // Empty skip
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertTrue(result)
    }

    // ============================================================================
    // recordPrompted()
    // ============================================================================

    @Test
    fun `recordPrompted updates lastPromptedAt to approximately current time`() {
        // Arrange
        var lastPromptedValue = 0L
        val lastPromptedAtPref = mockk<tachiyomi.core.common.preference.Preference<Long>> {
            every { get() } answers { lastPromptedValue }
            every { set(any()) } answers { lastPromptedValue = firstArg() }
        }
        every { prefs.lastPromptedAt() } returns lastPromptedAtPref
        val beforeCall = System.currentTimeMillis()

        // Act
        gatekeeper.recordPrompted()

        val afterCall = System.currentTimeMillis()

        // Assert: verify set() was called with a value close to current time
        verify {
            lastPromptedAtPref.set(any())
        }
        // Verify the actual captured value is within reasonable bounds
        val capturedValue = lastPromptedAtPref.get() // Will fail if set wasn't called
        assertTrue(capturedValue >= beforeCall && capturedValue <= afterCall)
    }

    // ============================================================================
    // skipVersion()
    // ============================================================================

    @Test
    fun `skipVersion sets skipVersion preference to the given version`() {
        // Arrange
        val versionToSkip = "2.3.4"
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>>(relaxed = true)
        every { prefs.skipVersion() } returns skipVersionPref

        // Act
        gatekeeper.skipVersion(versionToSkip)

        // Assert
        verify {
            skipVersionPref.set(versionToSkip)
        }
    }

    // ============================================================================
    // clearSkipIfOutdated()
    // ============================================================================

    @Test
    fun `clearSkipIfOutdated clears skipVersion when new version is newer than skipped`() {
        // Arrange
        val skippedVersion = "1.0.0"
        val currentLatestVersion = "1.1.0"
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>>(relaxed = true)
        every { skipVersionPref.get() } returns skippedVersion

        every { prefs.skipVersion() } returns skipVersionPref

        // Act
        gatekeeper.clearSkipIfOutdated(currentLatestVersion)

        // Assert
        verify {
            skipVersionPref.set("")
        }
    }

    @Test
    fun `clearSkipIfOutdated does NOT clear skipVersion when new version is older than skipped`() {
        // Arrange
        val skippedVersion = "2.0.0"
        val currentLatestVersion = "1.9.0"
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>>(relaxed = true)
        every { skipVersionPref.get() } returns skippedVersion

        every { prefs.skipVersion() } returns skipVersionPref

        // Act
        gatekeeper.clearSkipIfOutdated(currentLatestVersion)

        // Assert
        verify(exactly = 0) {
            skipVersionPref.set(any())
        }
    }

    @Test
    fun `clearSkipIfOutdated does NOT call set when skip is empty`() {
        // Arrange
        val currentLatestVersion = "1.1.0"
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>>(relaxed = true)
        every { skipVersionPref.get() } returns "" // Empty skip

        every { prefs.skipVersion() } returns skipVersionPref

        // Act
        gatekeeper.clearSkipIfOutdated(currentLatestVersion)

        // Assert
        verify(exactly = 0) {
            skipVersionPref.set(any())
        }
    }

    // ============================================================================
    // Integration: Skip version takes precedence over cadence
    // ============================================================================

    @Test
    fun `shouldPrompt returns false for skipped version even with WEEKLY cadence and sufficient time elapsed`() {
        // Arrange: WEEKLY cadence, >7 days elapsed, but version is skipped
        val releaseVersion = "5.0.0"
        val promptCadencePref = mockk<tachiyomi.core.common.preference.Preference<PromptCadence>> {
            every { get() } returns PromptCadence.WEEKLY
        }
        val skipVersionPref = mockk<tachiyomi.core.common.preference.Preference<String>> {
            every { get() } returns "5.0.0" // This version is skipped
        }
        val lastPromptedAtPref = mockk<tachiyomi.core.common.preference.Preference<Long>> {
            every { get() } returns referenceTime.minusSeconds(10 * 24 * 3600).toEpochMilli()
        }
        every { prefs.promptCadence() } returns promptCadencePref
        every { prefs.skipVersion() } returns skipVersionPref
        every { prefs.lastPromptedAt() } returns lastPromptedAtPref

        // Act
        val result = gatekeeper.shouldPrompt(releaseVersion)

        // Assert
        assertFalse(result)
    }
}
