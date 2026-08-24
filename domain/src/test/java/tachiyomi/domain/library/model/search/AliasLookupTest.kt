package tachiyomi.domain.library.model.search

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.maps.shouldContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

private enum class AliasProbe(vararg val aliases: String) {
    Alpha("a", "alpha"),
    Beta("b", "BETA"),
}

class AliasLookupTest {

    @Test
    fun `builds case-insensitive lookup for distinct aliases`() {
        val lookup = aliasLookup(AliasProbe.entries.toList()) { it.aliases }

        lookup.size shouldBe 4
        lookup shouldContain ("a" to AliasProbe.Alpha)
        lookup shouldContain ("alpha" to AliasProbe.Alpha)
        lookup shouldContain ("b" to AliasProbe.Beta)
        lookup["beta"] shouldBe AliasProbe.Beta
    }

    @Test
    fun `throws when two entries share an alias`() {
        val exception = shouldThrow<IllegalArgumentException> {
            aliasLookup(DuplicateProbe.entries.toList()) { it.aliases }
        }

        exception.message shouldBe "Duplicate enum aliases: shared"
    }
}

private enum class DuplicateProbe(vararg val aliases: String) {
    First("first", "shared"),
    Second("second", "shared"),
}
