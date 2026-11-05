package xyz.rayniyomi.cloudstream.host

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PluginQuarantineTest {

    @TempDir
    lateinit var directory: File

    private val key = PluginKey("MOOSAHub", "4a5df8bac1ada4e5d0", hostVersion = "579-1")

    @Test
    fun `a marked plugin returns its reason`() {
        val quarantine = PluginQuarantine(directory)

        quarantine.mark(key, "java.lang.NoClassDefFoundError: FragmentLifecycleCallbacks")

        PluginQuarantine(directory).reason(key)!! shouldContain "NoClassDefFoundError"
    }

    @Test
    fun `a new plugin file or a new host version is not quarantined`() {
        val quarantine = PluginQuarantine(directory)
        quarantine.mark(key, "crash")

        quarantine.reason(key.copy(fileSha256 = "91d7fd3be2d83cb6aa")) shouldBe null
        quarantine.reason(key.copy(hostVersion = "580-1")) shouldBe null
    }
}
