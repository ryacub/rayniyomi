package eu.kanade.tachiyomi.ui.entries.common

import kotlin.math.floor

object EntryListGapSeparator {
    fun <T, R> withMissingCount(
        items: List<T>,
        sortDescending: Boolean,
        itemId: (T) -> Long,
        itemNumber: (T) -> Double,
        calculateGap: (higher: T, lower: T) -> Int,
        mapItem: (T) -> R,
        mapMissing: (id: String, count: Int) -> R,
    ): List<R> {
        if (items.isEmpty()) return emptyList()
        val result = ArrayList<R>(items.size + 8)

        items.forEachIndexed { index, item ->
            if (index > 0) {
                val before = items[index - 1]
                val after = item
                val (lowerItem, higherItem) = if (sortDescending) {
                    after to before
                } else {
                    before to after
                }

                val gap = calculateGap(higherItem, lowerItem)
                if (gap > 0) {
                    result.add(mapMissing("${itemId(lowerItem)}-${itemId(higherItem)}", gap))
                }
            } else {
                val leadingGap = if (sortDescending) {
                    0
                } else {
                    floor(itemNumber(item))
                        .toInt()
                        .minus(1)
                        .coerceAtLeast(0)
                }

                if (leadingGap > 0) {
                    result.add(mapMissing("null-${itemId(item)}", leadingGap))
                }
            }
            result.add(mapItem(item))
        }

        return result
    }
}
