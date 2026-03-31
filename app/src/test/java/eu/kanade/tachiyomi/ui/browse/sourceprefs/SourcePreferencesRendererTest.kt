package eu.kanade.tachiyomi.ui.browse.sourceprefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SourcePreferencesRendererTest {

    @Test
    fun `resolveListPreferenceSummary uses summary when provided`() {
        val result = resolveListPreferenceSummary(
            summary = "Provided summary",
            currentValue = "a",
            entries = mapOf("a" to "Alpha"),
            unavailableTemplate = "Unavailable (%s)",
        )

        assertEquals("Provided summary", result)
    }

    @Test
    fun `resolveListPreferenceSummary uses mapped entry when available`() {
        val result = resolveListPreferenceSummary(
            summary = null,
            currentValue = "a",
            entries = mapOf("a" to "Alpha"),
            unavailableTemplate = "Unavailable (%s)",
        )

        assertEquals("Alpha", result)
    }

    @Test
    fun `resolveListPreferenceSummary falls back for stale value`() {
        val result = resolveListPreferenceSummary(
            summary = null,
            currentValue = "stale",
            entries = mapOf("a" to "Alpha"),
            unavailableTemplate = "Unavailable (%s)",
        )

        assertEquals("Unavailable (stale)", result)
    }

    @Test
    fun `formatUnsupportedTypeSubtitle uses key when present`() {
        val result = formatUnsupportedTypeSubtitle(
            template = "Unsupported preference type: %1\$s (%2\$s)",
            typeName = "FooPreference",
            key = "foo_key",
            keyFallback = "no key",
        )

        assertEquals("Unsupported preference type: FooPreference (foo_key)", result)
    }

    @Test
    fun `formatUnsupportedTypeSubtitle falls back when key is absent`() {
        val result = formatUnsupportedTypeSubtitle(
            template = "Unsupported preference type: %1\$s (%2\$s)",
            typeName = "FooPreference",
            key = null,
            keyFallback = "no key",
        )

        assertEquals("Unsupported preference type: FooPreference (no key)", result)
    }
}
