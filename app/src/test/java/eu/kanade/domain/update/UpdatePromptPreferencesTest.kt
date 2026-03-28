package eu.kanade.domain.update

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class UpdatePromptPreferencesTest {

    private lateinit var preferenceStore: PreferenceStore

    @BeforeEach
    fun setup() {
        preferenceStore = mockk()
    }

    // --- promptCadence() tests ---

    @Test
    fun `promptCadence default is ALWAYS`() {
        var cadenceValue = PromptCadence.ALWAYS
        val cadencePref: Preference<PromptCadence> = mockk {
            every { get() } answers { cadenceValue }
            every { set(any()) } answers { cadenceValue = firstArg() }
        }

        every {
            preferenceStore.getObject(
                Preference.privateKey("update_prompt_cadence"),
                PromptCadence.ALWAYS,
                any(),
                any(),
            )
        } returns cadencePref

        val prefs = UpdatePromptPreferences(preferenceStore)
        val result = prefs.promptCadence().get()

        assertEquals(PromptCadence.ALWAYS, result)
    }

    @Test
    fun `promptCadence can be set to DAILY and retrieved`() {
        var cadenceValue = PromptCadence.ALWAYS
        val cadencePref: Preference<PromptCadence> = mockk {
            every { get() } answers { cadenceValue }
            every { set(any()) } answers { cadenceValue = firstArg() }
        }

        every {
            preferenceStore.getObject(
                Preference.privateKey("update_prompt_cadence"),
                PromptCadence.ALWAYS,
                any(),
                any(),
            )
        } returns cadencePref

        val prefs = UpdatePromptPreferences(preferenceStore)
        prefs.promptCadence().set(PromptCadence.DAILY)

        assertEquals(PromptCadence.DAILY, prefs.promptCadence().get())
    }

    @Test
    fun `promptCadence can be set to WEEKLY and retrieved`() {
        var cadenceValue = PromptCadence.ALWAYS
        val cadencePref: Preference<PromptCadence> = mockk {
            every { get() } answers { cadenceValue }
            every { set(any()) } answers { cadenceValue = firstArg() }
        }

        every {
            preferenceStore.getObject(
                Preference.privateKey("update_prompt_cadence"),
                PromptCadence.ALWAYS,
                any(),
                any(),
            )
        } returns cadencePref

        val prefs = UpdatePromptPreferences(preferenceStore)
        prefs.promptCadence().set(PromptCadence.WEEKLY)

        assertEquals(PromptCadence.WEEKLY, prefs.promptCadence().get())
    }

    @Test
    fun `promptCadence can be set to NEVER and retrieved`() {
        var cadenceValue = PromptCadence.ALWAYS
        val cadencePref: Preference<PromptCadence> = mockk {
            every { get() } answers { cadenceValue }
            every { set(any()) } answers { cadenceValue = firstArg() }
        }

        every {
            preferenceStore.getObject(
                Preference.privateKey("update_prompt_cadence"),
                PromptCadence.ALWAYS,
                any(),
                any(),
            )
        } returns cadencePref

        val prefs = UpdatePromptPreferences(preferenceStore)
        prefs.promptCadence().set(PromptCadence.NEVER)

        assertEquals(PromptCadence.NEVER, prefs.promptCadence().get())
    }

    // --- skipVersion() tests ---

    @Test
    fun `skipVersion default is empty string`() {
        var skipVersionValue = ""
        val skipVersionPref: Preference<String> = mockk {
            every { get() } answers { skipVersionValue }
            every { set(any()) } answers { skipVersionValue = firstArg() }
        }

        every { preferenceStore.getString(Preference.appStateKey("update_skip_version"), "") } returns skipVersionPref

        val prefs = UpdatePromptPreferences(preferenceStore)
        val result = prefs.skipVersion().get()

        assertEquals("", result)
    }

    @Test
    fun `skipVersion can be set and retrieved`() {
        var skipVersionValue = ""
        val skipVersionPref: Preference<String> = mockk {
            every { get() } answers { skipVersionValue }
            every { set(any()) } answers { skipVersionValue = firstArg() }
        }

        every { preferenceStore.getString(Preference.appStateKey("update_skip_version"), "") } returns skipVersionPref

        val prefs = UpdatePromptPreferences(preferenceStore)
        prefs.skipVersion().set("1.2.3")

        assertEquals("1.2.3", prefs.skipVersion().get())
    }

    @Test
    fun `skipVersion can store version with v prefix`() {
        var skipVersionValue = ""
        val skipVersionPref: Preference<String> = mockk {
            every { get() } answers { skipVersionValue }
            every { set(any()) } answers { skipVersionValue = firstArg() }
        }

        every { preferenceStore.getString(Preference.appStateKey("update_skip_version"), "") } returns skipVersionPref

        val prefs = UpdatePromptPreferences(preferenceStore)
        prefs.skipVersion().set("v1.2.3")

        assertEquals("v1.2.3", prefs.skipVersion().get())
    }

    // --- lastPromptedAt() tests ---

    @Test
    fun `lastPromptedAt default is 0L`() {
        var lastPromptedValue = 0L
        val lastPromptedPref: Preference<Long> = mockk {
            every { get() } answers { lastPromptedValue }
            every { set(any()) } answers { lastPromptedValue = firstArg() }
        }

        every { preferenceStore.getLong(Preference.appStateKey("update_last_prompted"), 0L) } returns lastPromptedPref

        val prefs = UpdatePromptPreferences(preferenceStore)
        val result = prefs.lastPromptedAt().get()

        assertEquals(0L, result)
    }

    @Test
    fun `lastPromptedAt can be set and retrieved`() {
        var lastPromptedValue = 0L
        val lastPromptedPref: Preference<Long> = mockk {
            every { get() } answers { lastPromptedValue }
            every { set(any()) } answers { lastPromptedValue = firstArg() }
        }

        every { preferenceStore.getLong(Preference.appStateKey("update_last_prompted"), 0L) } returns lastPromptedPref

        val prefs = UpdatePromptPreferences(preferenceStore)
        val timestamp = System.currentTimeMillis()
        prefs.lastPromptedAt().set(timestamp)

        assertEquals(timestamp, prefs.lastPromptedAt().get())
    }

    @Test
    fun `lastPromptedAt can store large timestamp values`() {
        var lastPromptedValue = 0L
        val lastPromptedPref: Preference<Long> = mockk {
            every { get() } answers { lastPromptedValue }
            every { set(any()) } answers { lastPromptedValue = firstArg() }
        }

        every { preferenceStore.getLong(Preference.appStateKey("update_last_prompted"), 0L) } returns lastPromptedPref

        val prefs = UpdatePromptPreferences(preferenceStore)
        val timestamp = 1700000000000L // Nov 2023
        prefs.lastPromptedAt().set(timestamp)

        assertEquals(timestamp, prefs.lastPromptedAt().get())
    }

    // --- Key naming tests ---

    @Test
    fun `promptCadence uses privateKey for user-facing setting`() {
        var cadenceValue = PromptCadence.ALWAYS
        val cadencePref: Preference<PromptCadence> = mockk {
            every { get() } answers { cadenceValue }
            every { set(any()) } answers { cadenceValue = firstArg() }
        }

        every {
            preferenceStore.getObject(
                Preference.privateKey("update_prompt_cadence"),
                PromptCadence.ALWAYS,
                any(),
                any(),
            )
        } returns cadencePref

        val prefs = UpdatePromptPreferences(preferenceStore)
        prefs.promptCadence()

        // Verify that privateKey is used in the call
        verify {
            preferenceStore.getObject(
                Preference.privateKey("update_prompt_cadence"),
                PromptCadence.ALWAYS,
                any(),
                any(),
            )
        }
    }

    @Test
    fun `skipVersion uses appStateKey for mutable state`() {
        var skipVersionValue = ""
        val skipVersionPref: Preference<String> = mockk {
            every { get() } answers { skipVersionValue }
            every { set(any()) } answers { skipVersionValue = firstArg() }
        }

        every { preferenceStore.getString(Preference.appStateKey("update_skip_version"), "") } returns skipVersionPref

        val prefs = UpdatePromptPreferences(preferenceStore)
        prefs.skipVersion()

        // Verify that appStateKey is used in the call
        verify { preferenceStore.getString(Preference.appStateKey("update_skip_version"), "") }
    }

    @Test
    fun `lastPromptedAt uses appStateKey for mutable state`() {
        var lastPromptedValue = 0L
        val lastPromptedPref: Preference<Long> = mockk {
            every { get() } answers { lastPromptedValue }
            every { set(any()) } answers { lastPromptedValue = firstArg() }
        }

        every { preferenceStore.getLong(Preference.appStateKey("update_last_prompted"), 0L) } returns lastPromptedPref

        val prefs = UpdatePromptPreferences(preferenceStore)
        prefs.lastPromptedAt()

        // Verify that appStateKey is used in the call
        verify { preferenceStore.getLong(Preference.appStateKey("update_last_prompted"), 0L) }
    }

    // --- includePrerelease() tests ---

    @Test
    fun `includePrerelease default is false`() {
        var includePrereleaseValue = false
        val includePrereleasePref: Preference<Boolean> = mockk {
            every { get() } answers { includePrereleaseValue }
            every { set(any()) } answers { includePrereleaseValue = firstArg() }
        }

        every { preferenceStore.getBoolean(Preference.appStateKey("update_include_prerelease"), false) } returns
            includePrereleasePref

        val prefs = UpdatePromptPreferences(preferenceStore)
        val result = prefs.includePrerelease().get()

        assertEquals(false, result)
    }

    @Test
    fun `includePrerelease can be set to true and retrieved`() {
        var includePrereleaseValue = false
        val includePrereleasePref: Preference<Boolean> = mockk {
            every { get() } answers { includePrereleaseValue }
            every { set(any()) } answers { includePrereleaseValue = firstArg() }
        }

        every { preferenceStore.getBoolean(Preference.appStateKey("update_include_prerelease"), false) } returns
            includePrereleasePref

        val prefs = UpdatePromptPreferences(preferenceStore)
        prefs.includePrerelease().set(true)

        assertEquals(true, prefs.includePrerelease().get())
    }

    @Test
    fun `includePrerelease can be toggled back to false`() {
        var includePrereleaseValue = false
        val includePrereleasePref: Preference<Boolean> = mockk {
            every { get() } answers { includePrereleaseValue }
            every { set(any()) } answers { includePrereleaseValue = firstArg() }
        }

        every { preferenceStore.getBoolean(Preference.appStateKey("update_include_prerelease"), false) } returns
            includePrereleasePref

        val prefs = UpdatePromptPreferences(preferenceStore)
        prefs.includePrerelease().set(true)
        assertEquals(true, prefs.includePrerelease().get())

        prefs.includePrerelease().set(false)
        assertEquals(false, prefs.includePrerelease().get())
    }

    @Test
    fun `includePrerelease uses appStateKey for mutable state`() {
        var includePrereleaseValue = false
        val includePrereleasePref: Preference<Boolean> = mockk {
            every { get() } answers { includePrereleaseValue }
            every { set(any()) } answers { includePrereleaseValue = firstArg() }
        }

        every { preferenceStore.getBoolean(Preference.appStateKey("update_include_prerelease"), false) } returns
            includePrereleasePref

        val prefs = UpdatePromptPreferences(preferenceStore)
        prefs.includePrerelease()

        // Verify that appStateKey is used in the call
        verify { preferenceStore.getBoolean(Preference.appStateKey("update_include_prerelease"), false) }
    }
}
