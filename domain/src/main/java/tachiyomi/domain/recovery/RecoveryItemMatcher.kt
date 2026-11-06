package tachiyomi.domain.recovery

import tachiyomi.domain.recovery.model.RecoveryItem
import tachiyomi.domain.recovery.model.RecoveryItemPair

object RecoveryItemMatcher {

    fun suggest(original: List<RecoveryItem>, destination: List<RecoveryItem>): List<RecoveryItemPair> {
        val destinationByNumber = destination.uniqueByNumber()
        val originalByNumber = original.uniqueByNumber()
        return original.mapNotNull { item ->
            if (originalByNumber[item.number] !== item) return@mapNotNull null
            val match = destinationByNumber[item.number] ?: return@mapNotNull null
            RecoveryItemPair(originalUrl = item.url, destinationUrl = match.url, copyPosition = false)
        }
    }

    private fun List<RecoveryItem>.uniqueByNumber(): Map<Double, RecoveryItem> =
        filter { it.number >= 0 }
            .groupBy { it.number }
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
}
