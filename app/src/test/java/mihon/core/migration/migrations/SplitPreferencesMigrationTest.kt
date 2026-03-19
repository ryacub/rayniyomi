package mihon.core.migration.migrations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class SplitPreferencesMigrationTest {

    @Test
    fun `normalizeThemeModeValue uses locale independent uppercase`() {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))
        try {
            assertEquals("LIGHT", normalizeThemeModeValue("light"))
            assertEquals("SYSTEM", normalizeThemeModeValue("system"))
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
