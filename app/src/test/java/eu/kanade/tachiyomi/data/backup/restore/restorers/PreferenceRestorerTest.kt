package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category

class PreferenceRestorerTest {

    @Test
    fun `restore remaps every category filter preference by category name`() = runTest {
        val context = mockk<Context>(relaxed = true)
        mockkObject(AnimeLibraryUpdateJob.Companion)
        mockkObject(MangaLibraryUpdateJob.Companion)
        mockkObject(BackupCreateJob.Companion)
        every { AnimeLibraryUpdateJob.setupTask(any()) } returns Unit
        every { MangaLibraryUpdateJob.setupTask(any()) } returns Unit
        every { BackupCreateJob.setupTask(any()) } returns Unit

        try {
            val mangaCategories = mockk<GetMangaCategories> {
                coEvery { await() } returns listOf(category(id = 20, name = "Manga"))
            }
            val animeCategories = mockk<GetAnimeCategories> {
                coEvery { await() } returns listOf(category(id = 30, name = "Anime"))
            }

            categoryFilterPreferenceKeys().forEach { key ->
                var restored = emptySet<String>()
                val preference = mockk<Preference<Set<String>>>()
                every { preference.get() } answers { restored }
                every { preference.set(any()) } answers { restored = firstArg() }

                val preferenceStore = mockk<PreferenceStore>()
                every { preferenceStore.getAll() } answers { mapOf(key to restored) }
                every { preferenceStore.getStringSet(any(), any()) } returns preference

                PreferenceRestorer(
                    context = context,
                    getMangaCategories = mangaCategories,
                    getAnimeCategories = animeCategories,
                    preferenceStore = preferenceStore,
                ).restoreApp(
                    preferences = listOf(
                        BackupPreference(
                            key = key,
                            value = StringSetPreferenceValue(setOf("1", "2")),
                        ),
                    ),
                    backupCategories = listOf(
                        BackupCategory(id = 1, name = "Manga"),
                        BackupCategory(id = 2, name = "Anime"),
                    ),
                )

                assertEquals(setOf("20", "30"), restored, key)
            }
        } finally {
            unmockkObject(AnimeLibraryUpdateJob.Companion)
            unmockkObject(MangaLibraryUpdateJob.Companion)
            unmockkObject(BackupCreateJob.Companion)
        }
    }

    private fun category(id: Long, name: String) = Category(
        id = id,
        name = name,
        order = 0,
        flags = 0,
        hidden = false,
    )

    private fun categoryFilterPreferenceKeys() = listOf(
        "pref_filter_manga_updates_included_categories",
        "pref_filter_manga_updates_excluded_categories",
        "pref_filter_anime_updates_included_categories",
        "pref_filter_anime_updates_excluded_categories",
        "pref_filter_manga_upcoming_included_categories",
        "pref_filter_manga_upcoming_excluded_categories",
        "pref_filter_anime_upcoming_included_categories",
        "pref_filter_anime_upcoming_excluded_categories",
    )
}
