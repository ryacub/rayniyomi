package tachiyomi.domain.category.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CategoryHierarchyTest {

    @Test
    fun `flattenForDisplay keeps manual parent child order`() {
        val categories = listOf(
            category(id = 10, name = "Parent B", order = 1),
            category(id = 11, name = "Child B1", order = 2, parentId = 10),
            category(id = 20, name = "Parent A", order = 0),
            category(id = 21, name = "Child A1", order = 3, parentId = 20),
        )

        val ids = categories.flattenForDisplay().map { it.id }

        assertEquals(listOf(20L, 21L, 10L, 11L), ids)
    }

    @Test
    fun `flattenForDisplay sorts alphabetically when category flag enabled`() {
        val categories = listOf(
            category(id = 20, name = "Zeta", order = 0, flags = CategoryFlags.CATEGORY_FLAG_SORT_ALPHABETICAL),
            category(id = 21, name = "Child Zeta", order = 3, parentId = 20),
            category(id = 10, name = "Alpha", order = 1),
            category(id = 11, name = "Child Alpha", order = 2, parentId = 10),
        )

        val ids = categories.flattenForDisplay().map { it.id }

        assertEquals(listOf(10L, 11L, 20L, 21L), ids)
    }

    @Test
    fun `flattenForDisplay handles orphan child as top level`() {
        val categories = listOf(
            category(id = 30, name = "Orphan", order = 0, parentId = 999),
            category(id = 10, name = "Parent", order = 1),
        )

        val ids = categories.flattenForDisplay().map { it.id }

        assertEquals(listOf(30L, 10L), ids)
    }

    @Test
    fun `prefixedDisplayName includes parent name for child`() {
        val parent = category(id = 10, name = "Action", order = 0)
        val child = category(id = 11, name = "Isekai", order = 1, parentId = 10)

        assertEquals("Action / Isekai", child.prefixedDisplayName(mapOf(10L to parent)))
    }

    private fun category(
        id: Long,
        name: String,
        order: Long,
        flags: Long = 0,
        parentId: Long? = null,
    ) = Category(
        id = id,
        name = name,
        order = order,
        flags = flags,
        hidden = false,
        parentId = parentId,
    )
}
