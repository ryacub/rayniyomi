package tachiyomi.domain.recovery

import tachiyomi.domain.recovery.model.RecoveryIdentity
import tachiyomi.domain.recovery.model.RecoveryReviewReason

object RecoveryBatchValidator {

    data class Row(
        val entryId: Long,
        val original: RecoveryIdentity,
        val destination: RecoveryIdentity?,
    )

    fun blocked(rows: List<Row>): Map<Long, RecoveryReviewReason> {
        val originals = rows.associateBy { it.original }
        val shared = rows.mapNotNull { it.destination }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return rows.mapNotNull { row ->
            val destination = row.destination ?: return@mapNotNull null
            val reason = when {
                destination == row.original -> RecoveryReviewReason.SELF_TARGET
                destination in originals -> RecoveryReviewReason.DESTINATION_IS_ORIGINAL
                destination in shared -> RecoveryReviewReason.SHARED_DESTINATION
                else -> return@mapNotNull null
            }
            row.entryId to reason
        }.toMap()
    }
}
