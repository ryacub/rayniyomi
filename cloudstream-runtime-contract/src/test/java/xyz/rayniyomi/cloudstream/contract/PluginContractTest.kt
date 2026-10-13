package xyz.rayniyomi.cloudstream.contract

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PluginContractTest {

    @Test
    fun `every Rayniyomi host build may read plugins`() {
        listOf("xyz.rayniyomi", "xyz.rayniyomi.dev", "xyz.rayniyomi.debug", "xyz.rayniyomi.benchmark")
            .forEach { PluginContract.isHostPackage(it) shouldBe true }
    }

    @Test
    fun `other packages may not read plugins`() {
        listOf(null, "", "xyz.rayniyomix", "xyz.rayniyomi.evil", "com.example", "xyz.rayniyomi.cloudstream")
            .forEach { PluginContract.isHostPackage(it) shouldBe false }
    }

    @Test
    fun `authority and refresh action derive from the package names`() {
        PluginContract.authority("xyz.rayniyomi.cloudstream") shouldBe "xyz.rayniyomi.cloudstream.plugins"
        PluginContract.extensionReplacedAction("xyz.rayniyomi.dev") shouldBe
            "xyz.rayniyomi.dev.ACTION_EXTENSION_REPLACED"
    }
}
