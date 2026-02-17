package tachiyomi.domain.category.model

fun Category.hasAlphabeticalSortFlag(): Boolean {
    return flags and CategoryFlags.CATEGORY_FLAG_SORT_ALPHABETICAL != 0L
}

fun List<Category>.isAlphabeticalCategorySortEnabled(): Boolean {
    return any(Category::hasAlphabeticalSortFlag)
}

fun List<Category>.flattenForDisplay(): List<Category> {
    if (isEmpty()) return emptyList()

    val categoriesById = associateBy(Category::id)
    val alphabetical = isAlphabeticalCategorySortEnabled()
    val categoryComparator = if (alphabetical) {
        compareBy<Category> { it.name.lowercase() }.thenBy { it.id }
    } else {
        compareBy<Category> { it.order }.thenBy { it.id }
    }

    // Display model is intentionally depth-limited to two levels (root + direct child).
    val roots = filter { it.parentId == null || it.parentId !in categoriesById }
        .sortedWith(categoryComparator)
    val childrenByParent = filter { it.parentId != null && it.parentId in categoriesById }
        .groupBy { it.parentId }
        .mapValues { (_, value) -> value.sortedWith(categoryComparator) }

    val flattened = buildList {
        roots.forEach { root ->
            add(root)
            childrenByParent[root.id].orEmpty().forEach(::add)
        }
    }.toMutableList()

    if (flattened.size < size) {
        val included = flattened.asSequence().map { it.id }.toSet()
        flattened += filterNot { it.id in included }.sortedWith(categoryComparator)
    }

    return flattened
}

fun Category.prefixedDisplayName(categoriesById: Map<Long, Category>): String {
    val parentName = parentId?.let { categoriesById[it]?.name }
    return if (parentName != null) {
        "$parentName / $name"
    } else {
        name
    }
}
