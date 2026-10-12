package eu.kanade.tachiyomi.util

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class CrashLogUtilTest {

    @Test
    fun `report lists outdated and obsolete extensions in name order and skips current ones`() {
        val installed = listOf(
            InstalledExtensionVersion("Zeta", "pkg.zeta", "1.4.1", 1, isObsolete = true),
            InstalledExtensionVersion("Alpha", "pkg.alpha", "1.4.2", 2, isObsolete = false),
            InstalledExtensionVersion("Mid", "pkg.mid", "1.4.5", 5, isObsolete = false),
        )
        val available = mapOf(
            "pkg.alpha" to AvailableExtensionVersion("1.4.3", 3),
            "pkg.mid" to AvailableExtensionVersion("1.4.5", 5),
        )

        problematicExtensionsInfo(installed, available) shouldBe """
            Problematic extensions:
            - Alpha
              Installed: 1.4.2 / Available: 1.4.3
              Obsolete: false
            - Zeta
              Installed: 1.4.1 / Available: ?
              Obsolete: true
        """.trimIndent()
    }

    @Test
    fun `report is null when every extension is current`() {
        val installed = listOf(InstalledExtensionVersion("Alpha", "pkg.alpha", "1.4.2", 2, isObsolete = false))
        val available = mapOf("pkg.alpha" to AvailableExtensionVersion("1.4.2", 2))

        problematicExtensionsInfo(installed, available) shouldBe null
    }
}
