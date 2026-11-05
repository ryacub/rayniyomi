package xyz.rayniyomi.cloudstream.host

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.types.shouldBeSameInstanceAs
import org.junit.jupiter.api.Test

class PluginLinkCheckTest {

    @Test
    fun `a class whose lookup throws fails the plugin and names only the broken classes`() {
        val stored = NoClassDefFoundError("Failed resolution of: Landroidx/fragment/app/FragmentManager;")

        val error = shouldThrow<PluginLinkException> {
            requireLinkedClasses(listOf("a.Plugin", "a.Callback", "a.Provider")) { name ->
                if (name == "a.Callback") throw stored
            }
        }

        error.classes shouldContainExactly listOf("a.Callback")
        error.cause shouldBeSameInstanceAs stored
    }

    @Test
    fun `classes that never loaded or loaded cleanly pass`() {
        requireLinkedClasses(listOf("a.Plugin", "a.Provider")) { }
    }
}
