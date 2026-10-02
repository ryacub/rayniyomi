package eu.kanade.tachiyomi.source.anime

import android.graphics.drawable.Drawable
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.di.appGraph
import tachiyomi.data.source.anime.AnimeSourceGateway
import tachiyomi.domain.source.anime.model.StubAnimeSource
import tachiyomi.source.local.entries.anime.isLocal

fun AnimeSource.icon(): Drawable? = appGraph.animeExtensionManager.getAppIconForSource(this.id)

fun AnimeSource.getPreferenceKey(): String = "source_$id"

fun AnimeSource.toStubSource(): StubAnimeSource = StubAnimeSource(id = id, lang = lang, name = name)

fun AnimeSource.getNameForAnimeInfo(): String {
    val preferences = appGraph.sourcePreferences
    val enabledLanguages = preferences.enabledLanguages().get()
        .filterNot { it in listOf("all", "other") }
    val hasOneActiveLanguages = enabledLanguages.size == 1
    val isInEnabledLanguages = lang in enabledLanguages
    return when {
        // For edge cases where user disables a source they got manga of in their library.
        hasOneActiveLanguages && !isInEnabledLanguages -> AnimeSourceGateway.displayName(this)
        // Hide the language tag when only one language is used.
        hasOneActiveLanguages && isInEnabledLanguages -> name
        else -> AnimeSourceGateway.displayName(this)
    }
}

fun AnimeSource.isLocalOrStub(): Boolean = isLocal() || this is StubAnimeSource
