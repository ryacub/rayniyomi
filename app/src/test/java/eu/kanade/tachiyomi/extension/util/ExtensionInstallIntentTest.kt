package eu.kanade.tachiyomi.extension.util

import android.content.Intent
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ExtensionInstallIntentTest {

    private val key = "ExtensionInstaller.extra.DOWNLOAD_ID"

    @Test
    fun `returns null when the download id is missing`() {
        val intent = mockk<Intent> {
            every { getLongExtra(key, -1L) } returns -1L
            every { data } returns mockk<Uri>()
        }

        assertNull(parseExtensionInstallIntent(intent, key))
    }

    @Test
    fun `returns null when the apk uri is missing`() {
        val intent = mockk<Intent> {
            every { getLongExtra(key, -1L) } returns 42L
            every { data } returns null
        }

        assertNull(parseExtensionInstallIntent(intent, key))
    }

    @Test
    fun `returns the download id and apk uri`() {
        val uri = mockk<Uri>()
        val intent = mockk<Intent> {
            every { getLongExtra(key, -1L) } returns 42L
            every { data } returns uri
        }

        val result = parseExtensionInstallIntent(intent, key)

        assertEquals(42L, result?.downloadId)
        assertEquals(uri, result?.apkUri)
    }
}
