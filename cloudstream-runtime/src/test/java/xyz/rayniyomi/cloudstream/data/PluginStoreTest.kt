package xyz.rayniyomi.cloudstream.data

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class PluginStoreTest {

    private val dir = createTempDirectory("cs-store").toFile()
    private val file = File(dir, "state.json")

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `a change is saved and a new store reads it back`() {
        PluginStore(file) {}.update { it.giveConsent() }

        PluginStore(file) {}.state.value.consentGiven shouldBe true
    }

    @Test
    fun `a change notifies the host once`() {
        var changes = 0
        val store = PluginStore(file) { changes++ }

        store.update { it.giveConsent() }

        changes shouldBe 1
        store.state.value.consentGiven shouldBe true
    }

    @Test
    fun `an update that changes nothing does not write or notify`() {
        var changes = 0
        val store = PluginStore(file) { changes++ }

        store.update { it }

        changes shouldBe 0
        file.exists() shouldBe false
    }
}
