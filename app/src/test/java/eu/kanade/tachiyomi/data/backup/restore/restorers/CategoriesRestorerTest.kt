package eu.kanade.tachiyomi.data.backup.restore.restorers

import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.library.service.LibraryPreferences

class CategoriesRestorerTest {

    @Test
    fun `restorer remaps parent ids for child categories`() = runTest {
        val libraryPreferences = mockk<LibraryPreferences>()
        val categorizedDisplayPreference = mockk<Preference<Boolean>>(relaxed = true)
        every { libraryPreferences.categorizedDisplaySettings() } returns categorizedDisplayPreference

        val inserts = mutableListOf<InsertCall>()
        var nextId = 100L
        val restorer = CategoriesRestorer(
            getCategories = { emptyList() },
            insertCategory = { name, order, flags, parentId ->
                inserts += InsertCall(name, order, flags, parentId)
                nextId++
            },
            updateCategoryParent = { _, _ -> },
            libraryPreferences = libraryPreferences,
        )

        restorer(
            listOf(
                BackupCategory(name = "Action", order = 0, id = 1, flags = 0, parentId = null),
                BackupCategory(name = "Isekai", order = 1, id = 2, flags = 0, parentId = 1),
            ),
        )

        assertEquals(2, inserts.size)
        assertEquals("Action", inserts[0].name)
        assertEquals(null, inserts[0].parentId)
        assertEquals("Isekai", inserts[1].name)
        assertEquals(100L, inserts[1].parentId)
        verify { categorizedDisplayPreference.set(any()) }
    }

    @Test
    fun `restorer keeps backward compatible top level categories when no parent ids`() = runTest {
        val libraryPreferences = mockk<LibraryPreferences>()
        val categorizedDisplayPreference = mockk<Preference<Boolean>>(relaxed = true)
        every { libraryPreferences.categorizedDisplaySettings() } returns categorizedDisplayPreference

        val inserts = mutableListOf<InsertCall>()
        var nextId = 200L
        val restorer = CategoriesRestorer(
            getCategories = { emptyList() },
            insertCategory = { name, order, flags, parentId ->
                inserts += InsertCall(name, order, flags, parentId)
                nextId++
            },
            updateCategoryParent = { _, _ -> },
            libraryPreferences = libraryPreferences,
        )

        restorer(
            listOf(
                BackupCategory(name = "Action", order = 0, id = 1, flags = 0),
                BackupCategory(name = "Drama", order = 1, id = 2, flags = 0),
            ),
        )

        assertEquals(listOf(null, null), inserts.map { it.parentId })
        verify { categorizedDisplayPreference.set(any()) }
    }

    @Test
    fun `restorer updates existing category parent linkage to match backup hierarchy`() = runTest {
        val libraryPreferences = mockk<LibraryPreferences>()
        val categorizedDisplayPreference = mockk<Preference<Boolean>>(relaxed = true)
        every { libraryPreferences.categorizedDisplaySettings() } returns categorizedDisplayPreference

        val existing = listOf(
            Category(id = 10, name = "Action", order = 0, flags = 0, hidden = false, parentId = null),
            Category(id = 11, name = "Isekai", order = 1, flags = 0, hidden = false, parentId = null),
        )
        val parentUpdates = mutableListOf<Pair<Long, Long?>>()

        val restorer = CategoriesRestorer(
            getCategories = { existing },
            insertCategory = { _, _, _, _ -> error("No inserts expected") },
            updateCategoryParent = { id, parentId -> parentUpdates += id to parentId },
            libraryPreferences = libraryPreferences,
        )

        restorer(
            listOf(
                BackupCategory(name = "Action", order = 0, id = 1, flags = 0, parentId = null),
                BackupCategory(name = "Isekai", order = 1, id = 2, flags = 0, parentId = 1),
            ),
        )

        assertEquals(listOf(11L to 10L), parentUpdates)
        verify { categorizedDisplayPreference.set(any()) }
    }

    private data class InsertCall(
        val name: String,
        val order: Long,
        val flags: Long,
        val parentId: Long?,
    )
}
