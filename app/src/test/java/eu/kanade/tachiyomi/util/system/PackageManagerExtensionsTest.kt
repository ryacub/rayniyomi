package eu.kanade.tachiyomi.util.system

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PackageManagerExtensionsTest {

    @Test
    fun `package label uses package name when application info is absent`() {
        val packageInfo = PackageInfo().apply {
            packageName = "com.example.external"
        }

        assertEquals(
            "com.example.external",
            packageInfo.applicationLabelOrPackageName(mockk()),
        )
    }
}
