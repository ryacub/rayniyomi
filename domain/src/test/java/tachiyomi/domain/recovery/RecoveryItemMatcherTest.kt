package tachiyomi.domain.recovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.domain.recovery.model.RecoveryItem
import tachiyomi.domain.recovery.model.RecoveryItemPair

class RecoveryItemMatcherTest {

    private fun items(prefix: String, vararg numbers: Double) =
        numbers.mapIndexed { index, number ->
            RecoveryItem(url = "$prefix$index", name = "Item $number", number = number)
        }

    @Test
    fun `unique numbers pair one to one without a position copy`() {
        val pairs = RecoveryItemMatcher.suggest(items("o", 1.0, 2.0), items("d", 2.0, 1.0, 3.0))

        assertEquals(
            listOf(
                RecoveryItemPair("o0", "d1", copyPosition = false),
                RecoveryItemPair("o1", "d0", copyPosition = false),
            ),
            pairs,
        )
    }

    @Test
    fun `a number repeated on either side stays unmapped`() {
        val pairs = RecoveryItemMatcher.suggest(items("o", 1.0, 1.0, 2.0), items("d", 1.0, 2.0, 2.0, 3.0))

        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `unrecognized numbers stay unmapped`() {
        val pairs = RecoveryItemMatcher.suggest(items("o", -1.0, 4.0), items("d", -1.0, 4.0))

        assertEquals(listOf(RecoveryItemPair("o1", "d1", copyPosition = false)), pairs)
    }

    @Test
    fun `an offset numbering produces no pairs`() {
        val pairs = RecoveryItemMatcher.suggest(items("o", 1.0, 2.0, 3.0), items("d", 13.0, 14.0, 15.0))

        assertTrue(pairs.isEmpty())
    }
}
