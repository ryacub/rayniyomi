package eu.kanade.tachiyomi.ui.browse.extension

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InvalidExtensionDiagnosticsTest {

    @Test
    fun `diagnostics keep package version and truncate long signature hash`() {
        val diagnostics = InvalidExtensionDiagnostics.from(
            pkgName = "eu.kanade.tachiyomi.animeextension.en.allanime",
            versionName = "14.55",
            versionCode = 55L,
            signatureHash = "cbec121aa82ebb02aaa73806992e0368a97d47b5451ed6524816d03084c45905",
            debugDetail = "eu.kanade.tachiyomi.animeextension.en.allanime.AllAnime",
        )

        assertEquals("eu.kanade.tachiyomi.animeextension.en.allanime", diagnostics.pkgName)
        assertEquals("14.55 (55)", diagnostics.version)
        assertEquals("cbec121aa82e...", diagnostics.signatureHashDisplay)
        assertEquals(
            "eu.kanade.tachiyomi.animeextension.en.allanime.AllAnime",
            diagnostics.debugDetail,
        )
    }

    @Test
    fun `diagnostics omit blank debug detail and keep short signature hash without ellipsis`() {
        val diagnostics = InvalidExtensionDiagnostics.from(
            pkgName = "com.example.bad",
            versionName = "1.0",
            versionCode = 1L,
            signatureHash = "hash-z",
            debugDetail = "  ",
        )

        assertEquals("1.0 (1)", diagnostics.version)
        assertEquals("hash-z", diagnostics.signatureHashDisplay)
        assertNull(diagnostics.debugDetail)
    }
}
