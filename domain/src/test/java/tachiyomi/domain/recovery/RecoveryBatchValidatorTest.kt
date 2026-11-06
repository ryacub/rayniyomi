package tachiyomi.domain.recovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.recovery.model.RecoveryIdentity
import tachiyomi.domain.recovery.model.RecoveryReviewReason

class RecoveryBatchValidatorTest {

    private fun row(entryId: Long, original: String, destination: String?) = RecoveryBatchValidator.Row(
        entryId = entryId,
        original = RecoveryIdentity(sourceId = 1, url = original),
        destination = destination?.let { RecoveryIdentity(sourceId = if (it.startsWith("self")) 1 else 20, url = it.removePrefix("self:")) },
    )

    @Test
    fun `distinct destinations pass`() {
        val blocked = RecoveryBatchValidator.blocked(listOf(row(1, "a", "x"), row(2, "b", "y"), row(3, "c", null)))

        assertEquals(emptyMap<Long, RecoveryReviewReason>(), blocked)
    }

    @Test
    fun `two rows with one destination are both blocked`() {
        val blocked = RecoveryBatchValidator.blocked(listOf(row(1, "a", "x"), row(2, "b", "x"), row(3, "c", "z")))

        assertEquals(
            mapOf(1L to RecoveryReviewReason.SHARED_DESTINATION, 2L to RecoveryReviewReason.SHARED_DESTINATION),
            blocked,
        )
    }

    @Test
    fun `a row that targets its own original is blocked`() {
        val blocked = RecoveryBatchValidator.blocked(listOf(row(1, "a", "self:a")))

        assertEquals(mapOf(1L to RecoveryReviewReason.SELF_TARGET), blocked)
    }

    @Test
    fun `a row that targets another selected original is blocked`() {
        val blocked = RecoveryBatchValidator.blocked(listOf(row(1, "a", "self:b"), row(2, "b", "x")))

        assertEquals(mapOf(1L to RecoveryReviewReason.DESTINATION_IS_ORIGINAL), blocked)
    }
}
