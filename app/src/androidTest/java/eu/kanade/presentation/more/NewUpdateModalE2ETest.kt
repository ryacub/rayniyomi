package eu.kanade.presentation.more

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import eu.kanade.domain.update.PromptCadence
import eu.kanade.domain.update.UpdatePromptGatekeeper
import eu.kanade.domain.update.UpdatePromptPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.core.common.preference.AndroidPreferenceStore

/**
 * Instrumented E2E tests for the app update modal flow.
 *
 * Uses real AndroidPreferenceStore + SharedPreferences to verify:
 * 1. Gatekeeper allows prompt when stable release, cadence ALWAYS
 * 2. Gatekeeper blocks prompt when cadence is NEVER
 * 3. Skip version blocks the same version but allows a newer version
 * 4. Pre-release toggle: prerelease blocked when includePrerelease=false
 * 5. Pre-release toggle: prerelease allowed when includePrerelease=true
 * 6. Cadence DAILY: blocks re-prompt within 24 hours after recordPrompted()
 * 7. Cadence WEEKLY: blocks re-prompt within 7 days after recordPrompted()
 * 8. Preferences persist across new instances (real SharedPreferences)
 */
@RunWith(AndroidJUnit4::class)
class NewUpdateModalE2ETest {

    private lateinit var context: Context
    private lateinit var prefs: UpdatePromptPreferences
    private lateinit var gatekeeper: UpdatePromptGatekeeper

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().commit()
        prefs = UpdatePromptPreferences(AndroidPreferenceStore(context, sharedPreferences))
        gatekeeper = UpdatePromptGatekeeper(prefs)
    }

    // ============================================================================
    // TEST 1: Stable release with ALWAYS cadence — should prompt
    // ============================================================================

    @Test
    fun stableReleaseWithAlwaysCadenceShouldPrompt() {
        prefs.promptCadence().set(PromptCadence.ALWAYS)

        val result = gatekeeper.shouldPrompt("1.2.3", isPrerelease = false)

        assertTrue("Expected shouldPrompt=true for stable+ALWAYS, got false", result)
    }

    // ============================================================================
    // TEST 2: NEVER cadence — should never prompt
    // ============================================================================

    @Test
    fun neverCadenceBlocksPrompt() {
        prefs.promptCadence().set(PromptCadence.NEVER)

        val result = gatekeeper.shouldPrompt("1.2.3", isPrerelease = false)

        assertFalse("Expected shouldPrompt=false for NEVER cadence, got true", result)
    }

    // ============================================================================
    // TEST 3: Skip version blocks same version
    // ============================================================================

    @Test
    fun skipVersionBlocksSameVersion() {
        prefs.promptCadence().set(PromptCadence.ALWAYS)
        gatekeeper.skipVersion("1.2.3")

        val result = gatekeeper.shouldPrompt("1.2.3", isPrerelease = false)

        assertFalse("Expected shouldPrompt=false for skipped version, got true", result)
    }

    // ============================================================================
    // TEST 4: Skip version is cleared when newer version arrives
    // ============================================================================

    @Test
    fun skipVersionIsAutoCleared_WhenNewerVersionArrives() {
        prefs.promptCadence().set(PromptCadence.ALWAYS)
        gatekeeper.skipVersion("1.2.3")

        // Newer version comes in — clearSkipIfOutdated should clear "1.2.3"
        gatekeeper.clearSkipIfOutdated("1.3.0")
        val result = gatekeeper.shouldPrompt("1.3.0", isPrerelease = false)

        assertTrue("Expected shouldPrompt=true for new version after skip cleared, got false", result)
    }

    // ============================================================================
    // TEST 5: Pre-release blocked when includePrerelease=false
    // ============================================================================

    @Test
    fun prereleaseBlockedWhenIncludePrereleaseIsFalse() {
        prefs.promptCadence().set(PromptCadence.ALWAYS)
        prefs.includePrerelease().set(false)

        val result = gatekeeper.shouldPrompt("1.2.3-beta.1", isPrerelease = true)

        assertFalse("Expected shouldPrompt=false for prerelease with includePrerelease=false", result)
    }

    // ============================================================================
    // TEST 6: Pre-release allowed when includePrerelease=true
    // ============================================================================

    @Test
    fun prereleaseAllowedWhenIncludePrereleaseIsTrue() {
        prefs.promptCadence().set(PromptCadence.ALWAYS)
        prefs.includePrerelease().set(true)

        val result = gatekeeper.shouldPrompt("1.2.3-beta.1", isPrerelease = true)

        assertTrue("Expected shouldPrompt=true for prerelease with includePrerelease=true", result)
    }

    // ============================================================================
    // TEST 7: Stable release is NOT affected by includePrerelease setting
    // ============================================================================

    @Test
    fun stableReleaseAllowedRegardlessOfIncludePrereleaseSetting() {
        prefs.promptCadence().set(PromptCadence.ALWAYS)
        prefs.includePrerelease().set(false)

        val result = gatekeeper.shouldPrompt("1.2.3", isPrerelease = false)

        assertTrue("Expected shouldPrompt=true for stable release regardless of includePrerelease, got false", result)
    }

    // ============================================================================
    // TEST 8: DAILY cadence blocks re-prompt within 24 hours
    // ============================================================================

    @Test
    fun dailyCadenceBlocksRepromptWithin24Hours() {
        prefs.promptCadence().set(PromptCadence.DAILY)
        // Record prompt just now (within 24-hour window)
        prefs.lastPromptedAt().set(System.currentTimeMillis())

        val result = gatekeeper.shouldPrompt("1.2.3", isPrerelease = false)

        assertFalse("Expected shouldPrompt=false within 24h after DAILY prompt, got true", result)
    }

    // ============================================================================
    // TEST 9: DAILY cadence allows re-prompt after 24+ hours
    // ============================================================================

    @Test
    fun dailyCadenceAllowsRepromptAfter24Hours() {
        prefs.promptCadence().set(PromptCadence.DAILY)
        val twentyFiveHoursAgoMs = System.currentTimeMillis() - (25 * 60 * 60 * 1000L)
        prefs.lastPromptedAt().set(twentyFiveHoursAgoMs)

        val result = gatekeeper.shouldPrompt("1.2.3", isPrerelease = false)

        assertTrue("Expected shouldPrompt=true after 25h with DAILY cadence, got false", result)
    }

    // ============================================================================
    // TEST 10: WEEKLY cadence blocks re-prompt within 7 days
    // ============================================================================

    @Test
    fun weeklyCadenceBlocksRepromptWithin7Days() {
        prefs.promptCadence().set(PromptCadence.WEEKLY)
        val threeDaysAgoMs = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000L)
        prefs.lastPromptedAt().set(threeDaysAgoMs)

        val result = gatekeeper.shouldPrompt("1.2.3", isPrerelease = false)

        assertFalse("Expected shouldPrompt=false within 7 days after WEEKLY prompt, got true", result)
    }

    // ============================================================================
    // TEST 11: WEEKLY cadence allows re-prompt after 7+ days
    // ============================================================================

    @Test
    fun weeklyCadenceAllowsRepromptAfter7Days() {
        prefs.promptCadence().set(PromptCadence.WEEKLY)
        val eightDaysAgoMs = System.currentTimeMillis() - (8 * 24 * 60 * 60 * 1000L)
        prefs.lastPromptedAt().set(eightDaysAgoMs)

        val result = gatekeeper.shouldPrompt("1.2.3", isPrerelease = false)

        assertTrue("Expected shouldPrompt=true after 8 days with WEEKLY cadence, got false", result)
    }

    // ============================================================================
    // TEST 12: recordPrompted persists timestamp — new gatekeeper instance sees it
    // ============================================================================

    @Test
    fun recordPromptedPersistsAcrossNewGatekeeperInstance() {
        prefs.promptCadence().set(PromptCadence.DAILY)
        gatekeeper.recordPrompted()

        // Create a new gatekeeper instance with the same prefs (same SharedPreferences)
        val newGatekeeper = UpdatePromptGatekeeper(prefs)
        val result = newGatekeeper.shouldPrompt("1.2.3", isPrerelease = false)

        assertFalse("Expected shouldPrompt=false — recordPrompted persists across instances", result)
    }

    // ============================================================================
    // TEST 13: skipVersion persists across new gatekeeper instances
    // ============================================================================

    @Test
    fun skipVersionPersistsAcrossNewGatekeeperInstance() {
        prefs.promptCadence().set(PromptCadence.ALWAYS)
        gatekeeper.skipVersion("1.2.3")

        val newGatekeeper = UpdatePromptGatekeeper(prefs)
        val result = newGatekeeper.shouldPrompt("1.2.3", isPrerelease = false)

        assertFalse("Expected shouldPrompt=false — skipVersion persists across instances", result)
    }

    // ============================================================================
    // TEST 14: DAILY cadence — first prompt ever (lastPromptedAt=0) should allow
    // ============================================================================

    @Test
    fun dailyCadenceFirstEverPromptAllowed() {
        prefs.promptCadence().set(PromptCadence.DAILY)
        // lastPromptedAt defaults to 0L — never prompted before

        val result = gatekeeper.shouldPrompt("1.2.3", isPrerelease = false)

        assertTrue("Expected shouldPrompt=true on first ever prompt with DAILY cadence, got false", result)
    }

    private companion object {
        const val PREFS_NAME = "new_update_modal_e2e_test_prefs"
    }
}
