package eu.kanade.tachiyomi.ui.entries.common

internal interface SelectableEntryItem {
    val id: Long
    val selected: Boolean
}

internal class EntrySelectionController(
    private val selectedIds: MutableSet<Long>,
) {
    private class SelectionRangeState(
        var first: Int = -1,
        var last: Int = -1,
    )

    private val rangeState = SelectionRangeState()

    fun <T : SelectableEntryItem> toggleSelection(
        visibleItems: List<T>,
        allItems: List<T>,
        itemId: Long,
        selected: Boolean,
        userSelected: Boolean,
        fromLongPress: Boolean,
        updateSelection: (T, Boolean) -> T,
    ): List<T> {
        val selectedIndex = visibleItems.indexOfFirst { it.id == itemId }
        if (selectedIndex < 0) return allItems

        val allIndexById = allItems.indices.associateBy { allItems[it].id }
        val selectedCanonicalIndex = allIndexById[itemId] ?: return allItems
        val newItems = allItems.toMutableList()

        val selectedItem = newItems[selectedCanonicalIndex]
        if ((selectedItem.selected && selected) || (!selectedItem.selected && !selected)) return allItems

        val firstSelection = selectedIds.isEmpty()
        newItems[selectedCanonicalIndex] = updateSelection(selectedItem, selected)
        addOrRemoveSelection(itemId, selected)

        if (selected && userSelected && fromLongPress) {
            if (firstSelection) {
                rangeState.first = selectedIndex
                rangeState.last = selectedIndex
            } else {
                val range: IntRange = when {
                    selectedIndex < rangeState.first -> {
                        val computed = selectedIndex + 1..<rangeState.first
                        rangeState.first = selectedIndex
                        computed
                    }
                    selectedIndex > rangeState.last -> {
                        val computed = (rangeState.last + 1)..<selectedIndex
                        rangeState.last = selectedIndex
                        computed
                    }
                    else -> IntRange.EMPTY
                }

                range.forEach { index ->
                    val inbetweenId = visibleItems[index].id
                    val inbetweenCanonicalIndex = allIndexById[inbetweenId] ?: return@forEach
                    val inbetweenItem = newItems[inbetweenCanonicalIndex]
                    if (!inbetweenItem.selected) {
                        addOrRemoveSelection(inbetweenItem.id, true)
                        newItems[inbetweenCanonicalIndex] = updateSelection(inbetweenItem, true)
                    }
                }
            }
        } else if (userSelected && !fromLongPress) {
            if (!selected) {
                if (selectedIndex == rangeState.first) {
                    val reanchoredFirst = visibleItems.indexOfFirst { it.id in selectedIds }
                    if (reanchoredFirst == -1) {
                        resetRange()
                    } else {
                        rangeState.first = reanchoredFirst
                    }
                } else if (selectedIndex == rangeState.last) {
                    val reanchoredLast = visibleItems.indexOfLast { it.id in selectedIds }
                    if (reanchoredLast == -1) {
                        resetRange()
                    } else {
                        rangeState.last = reanchoredLast
                    }
                }
            } else {
                if (rangeState.first == -1 || selectedIndex < rangeState.first) {
                    rangeState.first = selectedIndex
                }
                if (rangeState.last == -1 || selectedIndex > rangeState.last) {
                    rangeState.last = selectedIndex
                }
            }
        }

        return newItems
    }

    fun <T : SelectableEntryItem> toggleAllSelection(
        items: List<T>,
        selected: Boolean,
        updateSelection: (T, Boolean) -> T,
    ): List<T> {
        val newItems = items.map {
            addOrRemoveSelection(it.id, selected)
            updateSelection(it, selected)
        }
        resetRange()
        return newItems
    }

    fun <T : SelectableEntryItem> invertSelection(
        items: List<T>,
        updateSelection: (T, Boolean) -> T,
    ): List<T> {
        val newItems = items.map {
            val newSelected = !it.selected
            addOrRemoveSelection(it.id, newSelected)
            updateSelection(it, newSelected)
        }
        resetRange()
        return newItems
    }

    private fun addOrRemoveSelection(id: Long, selected: Boolean) {
        if (selected) {
            selectedIds.add(id)
        } else {
            selectedIds.remove(id)
        }
    }

    private fun resetRange() {
        rangeState.first = -1
        rangeState.last = -1
    }
}
