package tachiyomi.presentation.widget.di

import android.app.Application
import eu.kanade.tachiyomi.core.security.SecurityPreferences
import tachiyomi.domain.updates.anime.interactor.GetAnimeUpdates
import tachiyomi.domain.updates.manga.interactor.GetMangaUpdates

interface WidgetGraph {
    val application: Application
    val getMangaUpdates: GetMangaUpdates
    val getAnimeUpdates: GetAnimeUpdates
    val securityPreferences: SecurityPreferences
}
