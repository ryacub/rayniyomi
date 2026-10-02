package eu.kanade.tachiyomi.data.backup.restore.restorers

import android.content.Context
import android.util.Log
import eu.kanade.tachiyomi.data.backup.create.BackupCreateJob
import eu.kanade.tachiyomi.data.backup.models.BackupCategory
import eu.kanade.tachiyomi.data.backup.models.BackupPreference
import eu.kanade.tachiyomi.data.backup.models.BackupSourcePreferences
import eu.kanade.tachiyomi.data.backup.models.BooleanPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.FloatPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.IntPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.LongPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringPreferenceValue
import eu.kanade.tachiyomi.data.backup.models.StringSetPreferenceValue
import eu.kanade.tachiyomi.data.library.anime.AnimeLibraryUpdateJob
import eu.kanade.tachiyomi.data.library.manga.MangaLibraryUpdateJob
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.source.sourcePreferences
import tachiyomi.core.common.preference.AndroidPreferenceStore
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.plusAssign
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.manga.interactor.GetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.library.service.LibraryPreferences

class PreferenceRestorer(
    private val context: Context,
    private val getMangaCategories: GetMangaCategories = appGraph.getMangaCategories,
    private val getAnimeCategories: GetAnimeCategories = appGraph.getAnimeCategories,
    private val preferenceStore: PreferenceStore = appGraph.preferenceStore,
) {
    suspend fun restoreApp(
        preferences: List<BackupPreference>,
        backupMangaCategories: List<BackupCategory>?,
        backupAnimeCategories: List<BackupCategory>?,
    ) {
        val categoryMappings = CategoryMappings(
            manga = CategoryMapping(
                backupMangaCategories.orEmpty(),
                if (backupMangaCategories != null) getMangaCategories.await() else emptyList(),
            ),
            anime = CategoryMapping(
                backupAnimeCategories.orEmpty(),
                if (backupAnimeCategories != null) getAnimeCategories.await() else emptyList(),
            ),
        )
        restorePreferences(preferences, preferenceStore, categoryMappings)

        AnimeLibraryUpdateJob.setupTask(context)
        MangaLibraryUpdateJob.setupTask(context)
        BackupCreateJob.setupTask(context)
    }

    fun restoreSource(preferences: List<BackupSourcePreferences>) {
        preferences.forEach {
            val sourcePrefs = AndroidPreferenceStore(context, sourcePreferences(it.sourceKey))
            restorePreferences(it.prefs, sourcePrefs)
        }
    }

    private fun restorePreferences(
        toRestore: List<BackupPreference>,
        preferenceStore: PreferenceStore,
        categoryMappings: CategoryMappings = CategoryMappings.NONE,
    ) {
        val prefs = preferenceStore.getAll()
        toRestore.forEach { (key, value) ->
            try {
                when (value) {
                    is IntPreferenceValue -> {
                        if (prefs[key] is Int?) {
                            val mapping = categoryMappings.forKey(key)
                            val newValue = if (mapping != null) {
                                mapping.destinationId(value.value.toString())?.toInt()
                            } else {
                                value.value
                            }

                            newValue?.let { preferenceStore.getInt(key).set(it) }
                        }
                    }
                    is LongPreferenceValue -> {
                        if (prefs[key] is Long?) {
                            preferenceStore.getLong(key).set(value.value)
                        }
                    }
                    is FloatPreferenceValue -> {
                        if (prefs[key] is Float?) {
                            preferenceStore.getFloat(key).set(value.value)
                        }
                    }
                    is StringPreferenceValue -> {
                        if (prefs[key] is String?) {
                            preferenceStore.getString(key).set(value.value)
                        }
                    }
                    is BooleanPreferenceValue -> {
                        if (prefs[key] is Boolean?) {
                            preferenceStore.getBoolean(key).set(value.value)
                        }
                    }
                    is StringSetPreferenceValue -> {
                        if (prefs[key] is Set<*>?) {
                            restoreStringSet(
                                preferenceStore.getStringSet(key),
                                value.value,
                                categoryMappings.forKey(key),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PreferenceRestorer", "Failed to restore preference <$key>", e)
            }
        }
    }

    private fun restoreStringSet(
        preference: Preference<Set<String>>,
        value: Set<String>,
        mapping: CategoryMapping?,
    ) {
        if (mapping == null) {
            preference.set(value)
            return
        }

        val ids = value.mapNotNull { mapping.destinationId(it)?.toString() }
        if (ids.isNotEmpty()) {
            preference += ids
        }
    }
}

private class CategoryMappings(
    private val manga: CategoryMapping,
    private val anime: CategoryMapping,
) {
    fun forKey(key: String): CategoryMapping? = when (key) {
        in LibraryPreferences.mangaCategoryPreferenceKeys,
        in DownloadPreferences.mangaCategoryPreferenceKeys,
        -> manga
        in LibraryPreferences.animeCategoryPreferenceKeys,
        in DownloadPreferences.animeCategoryPreferenceKeys,
        -> anime
        else -> null
    }

    companion object {
        val NONE = CategoryMappings(CategoryMapping.EMPTY, CategoryMapping.EMPTY)
    }
}

private class CategoryMapping(
    backupCategories: List<BackupCategory>,
    destinationCategories: List<Category>,
) {
    private val backupCategoriesById = backupCategories.associateBy { it.id.toString() }
    private val destinationCategoriesByName = destinationCategories.associateBy { it.name }

    fun destinationId(backupId: String): Long? =
        backupCategoriesById[backupId]?.let { destinationCategoriesByName[it.name]?.id }

    companion object {
        val EMPTY = CategoryMapping(emptyList(), emptyList())
    }
}
