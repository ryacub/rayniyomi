package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.PreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category

class PreferenceRestorerTest {

    private val context = mockk<Context>(relaxed = true)

    // Both databases contain both names, so a lookup through the wrong mapping gives a wrong ID.
    private val mangaCategories = mockk<GetMangaCategories> {
        coEvery { await() } returns listOf(category(id = 20, name = "Comics"), category(id = 21, name = "Watch"))
    }
    private val animeCategories = mockk<GetAnimeCategories> {
        coEvery { await() } returns listOf(category(id = 30, name = "Watch"), category(id = 31, name = "Comics"))
    }

    // The two backup lists use the same ID for categories with different names.
    private val backupMangaCategories = listOf(BackupCategory(id = 1, name = "Comics"))
    private val backupAnimeCategories = listOf(BackupCategory(id = 1, name = "Watch"))

    @BeforeEach
    fun setUp() {
        mockkObject(AnimeLibraryUpdateJob.Companion)
        mockkObject(MangaLibraryUpdateJob.Companion)
        mockkObject(BackupCreateJob.Companion)
        every { AnimeLibraryUpdateJob.setupTask(any()) } returns Unit
        every { MangaLibraryUpdateJob.setupTask(any()) } returns Unit
        every { BackupCreateJob.setupTask(any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(AnimeLibraryUpdateJob.Companion)
        unmockkObject(MangaLibraryUpdateJob.Companion)
        unmockkObject(BackupCreateJob.Companion)
    }

    @Test
    fun `restore remaps manga category filters through the manga mapping only`() = runTest {
        mangaCategoryFilterKeys.forEach { key ->
            assertEquals(setOf("20"), restoreStringSet(key, setOf("1")), key)
        }
    }

    @Test
    fun `restore remaps anime category filters through the anime mapping only`() = runTest {
        animeCategoryFilterKeys.forEach { key ->
            assertEquals(setOf("30"), restoreStringSet(key, setOf("1")), key)
        }
    }

    @Test
    fun `restore resolves the manga default category through the manga mapping`() = runTest {
        assertEquals(20, restoreInt("default_category", 1))
    }

    @Test
    fun `restore resolves the anime default category through the anime mapping`() = runTest {
        assertEquals(30, restoreInt("default_anime_category", 1))
    }

    @Test
    fun `restore skips category preferences when the backup has no categories`() = runTest {
        (mangaCategoryFilterKeys + animeCategoryFilterKeys).forEach { key ->
            assertEquals(emptySet<String>(), restoreStringSet(key, setOf("1"), withCategories = false), key)
        }
        assertEquals(null, restoreInt("default_anime_category", 1, withCategories = false))
    }

    private suspend fun restoreStringSet(
        key: String,
        value: Set<String>,
        withCategories: Boolean = true,
    ): Set<String> {
        var restored = emptySet<String>()
        val preference = mockk<Preference<Set<String>>>()
        every { preference.get() } answers { restored }
        every { preference.set(any()) } answers { restored = firstArg() }

        val preferenceStore = mockk<PreferenceStore>()
        every { preferenceStore.getAll() } answers { mapOf(key to restored) }
        every { preferenceStore.getStringSet(any(), any()) } returns preference

        restore(preferenceStore, key, StringSetPreferenceValue(value), withCategories)
        return restored
    }

    private suspend fun restoreInt(key: String, value: Int, withCategories: Boolean = true): Int? {
        var restored: Int? = null
        val preference = mockk<Preference<Int>>()
        every { preference.set(any()) } answers { restored = firstArg() }

        val preferenceStore = mockk<PreferenceStore>()
        every { preferenceStore.getAll() } answers { mapOf(key to restored) }
        every { preferenceStore.getInt(any(), any()) } returns preference

        restore(preferenceStore, key, IntPreferenceValue(value), withCategories)
        return restored
    }

    private suspend fun restore(
        preferenceStore: PreferenceStore,
        key: String,
        value: PreferenceValue,
        withCategories: Boolean,
    ) {
        PreferenceRestorer(
            context = context,
            getMangaCategories = mangaCategories,
            getAnimeCategories = animeCategories,
            preferenceStore = preferenceStore,
        ).restoreApp(
            preferences = listOf(BackupPreference(key = key, value = value)),
            backupMangaCategories = backupMangaCategories.takeIf { withCategories },
            backupAnimeCategories = backupAnimeCategories.takeIf { withCategories },
        )
    }

    private fun category(id: Long, name: String) = Category(
        id = id,
        name = name,
        order = 0,
        flags = 0,
        hidden = false,
    )

    private val mangaCategoryFilterKeys = listOf(
        "library_update_categories",
        "library_update_categories_exclude",
        "pref_filter_manga_updates_included_categories",
        "pref_filter_manga_updates_excluded_categories",
        "pref_filter_manga_upcoming_included_categories",
        "pref_filter_manga_upcoming_excluded_categories",
        "remove_exclude_categories",
        "download_new_categories",
        "download_new_categories_exclude",
    )

    private val animeCategoryFilterKeys = listOf(
        "animelib_update_categories",
        "animelib_update_categories_exclude",
        "pref_filter_anime_updates_included_categories",
        "pref_filter_anime_updates_excluded_categories",
        "pref_filter_anime_upcoming_included_categories",
        "pref_filter_anime_upcoming_excluded_categories",
        "remove_exclude_anime_categories",
        "download_new_anime_categories",
        "download_new_anime_categories_exclude",
    )
}
