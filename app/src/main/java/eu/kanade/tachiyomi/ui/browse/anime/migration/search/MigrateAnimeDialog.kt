package eu.kanade.tachiyomi.ui.browse.anime.migration.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.hasCustomBackground
import eu.kanade.domain.entries.anime.model.hasCustomCover
import eu.kanade.domain.entries.anime.model.toSAnime
import eu.kanade.domain.items.episode.interactor.SyncEpisodesWithSource
import eu.kanade.presentation.components.IndicatorSize
import eu.kanade.presentation.util.StateViewModel
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.data.cache.AnimeBackgroundCache
import eu.kanade.tachiyomi.data.cache.AnimeCoverCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.ui.browse.anime.migration.AnimeMigrationFlags
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update
import logcat.LogPriority
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.data.source.anime.AnimeSourceGateway
import tachiyomi.domain.category.anime.interactor.GetAnimeCategories
import tachiyomi.domain.category.anime.interactor.SetAnimeCategories
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.toEpisodeUpdate
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.interactor.InsertAnimeTrack
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.components.LabeledCheckbox
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.LoadingScreen
import java.time.Instant

@Composable
internal fun MigrateAnimeDialog(
    oldAnime: Anime,
    newAnime: Anime,
    screenModel: MigrateAnimeDialogScreenModel,
    onDismissRequest: () -> Unit,
    onClickTitle: () -> Unit,
    onClickSeasons: () -> Unit,
    onPopScreen: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by screenModel.state.collectAsStateWithLifecycle()

    val flags = remember { AnimeMigrationFlags.getFlags(oldAnime, screenModel.migrateFlags.get()) }
    val selectedFlags = remember { flags.map { it.isDefaultSelected }.toMutableStateList() }

    fun migrate(replace: Boolean) {
        scope.launchIO {
            val migrated = screenModel.migrateAnime(
                oldAnime,
                newAnime,
                replace,
                AnimeMigrationFlags.getSelectedFlagsBitMap(selectedFlags, flags),
            )
            withUIContext {
                if (migrated) onPopScreen() else context.toast(AYMR.strings.migration_failed)
            }
        }
    }
    val canMigrate = remember { oldAnime.fetchType == newAnime.fetchType }

    if (state.isMigrating) {
        LoadingScreen(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = {
                Text(text = stringResource(MR.strings.migration_dialog_what_to_include))
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    if (canMigrate) {
                        flags.forEachIndexed { index, flag ->
                            LabeledCheckbox(
                                label = stringResource(flag.titleId),
                                checked = selectedFlags[index],
                                onCheckedChange = { selectedFlags[index] = it },
                            )
                        }
                    } else {
                        val message = if (oldAnime.fetchType == FetchType.Seasons) {
                            AYMR.strings.label_cant_migrate_season
                        } else {
                            AYMR.strings.label_cant_migrate_episode
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(IndicatorSize),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                text = stringResource(message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            onClickTitle()
                        },
                    ) {
                        Text(text = stringResource(AYMR.strings.action_show_anime))
                    }

                    if (newAnime.fetchType != FetchType.Episodes) {
                        TextButton(
                            onClick = {
                                onDismissRequest()
                                onClickSeasons()
                            },
                        ) {
                            Text(text = stringResource(AYMR.strings.label_show_seasons))
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (canMigrate) {
                        TextButton(onClick = { migrate(replace = false) }) {
                            Text(text = stringResource(MR.strings.copy))
                        }
                        TextButton(onClick = { migrate(replace = true) }) {
                            Text(text = stringResource(MR.strings.migrate))
                        }
                    }
                }
            },
        )
    }
}

@Inject
@ViewModelKey
@ContributesIntoMap(AppScope::class, binding<ViewModel>())
internal class MigrateAnimeDialogScreenModel(
    private val sourceManager: AnimeSourceManager,
    private val downloadManager: AnimeDownloadManager,
    private val updateAnime: UpdateAnime,
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId,
    private val syncEpisodesWithSource: SyncEpisodesWithSource,
    private val updateEpisode: UpdateEpisode,
    private val getCategories: GetAnimeCategories,
    private val setAnimeCategories: SetAnimeCategories,
    private val getTracks: GetAnimeTracks,
    private val insertTrack: InsertAnimeTrack,
    private val coverCache: AnimeCoverCache,
    private val backgroundCache: AnimeBackgroundCache,
    private val preferenceStore: PreferenceStore,
    private val trackerManager: TrackerManager,
) : StateViewModel<MigrateAnimeDialogScreenModel.State>(State()) {

    val migrateFlags: Preference<Int> by lazy {
        preferenceStore.getInt("migrate_flags", Int.MAX_VALUE)
    }

    private val enhancedServices by lazy {
        trackerManager.trackers.filterIsInstance<EnhancedAnimeTracker>()
    }

    suspend fun migrateAnime(
        oldAnime: Anime,
        newAnime: Anime,
        replace: Boolean,
        flags: Int,
    ): Boolean {
        migrateFlags.set(flags)
        val source = sourceManager.get(newAnime.source) ?: return false
        val prevSource = sourceManager.get(oldAnime.source)

        mutableState.update { it.copy(isMigrating = true) }

        val migrated = try {
            val episodes = AnimeSourceGateway.episodes(source, newAnime.toSAnime())

            migrateAnimeInternal(
                oldSource = prevSource,
                newSource = source,
                oldAnime = oldAnime,
                newAnime = newAnime,
                sourceEpisodes = episodes,
                replace = replace,
                flags = flags,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e)
            false
        }
        if (!migrated) mutableState.update { it.copy(isMigrating = false) }
        return migrated
    }

    private suspend fun migrateAnimeInternal(
        oldSource: AnimeSource?,
        newSource: AnimeSource,
        oldAnime: Anime,
        newAnime: Anime,
        sourceEpisodes: List<SEpisode>,
        replace: Boolean,
        flags: Int,
    ): Boolean {
        val migrateEpisodes = AnimeMigrationFlags.hasEpisodes(flags)
        val migrateCategories = AnimeMigrationFlags.hasCategories(flags)
        val migrateCustomCover = AnimeMigrationFlags.hasCustomCover(flags)
        val migrateCustomBackground = AnimeMigrationFlags.hasCustomBackground(flags)
        val deleteDownloaded = AnimeMigrationFlags.hasDeleteDownloaded(flags)

        try {
            syncEpisodesWithSource.await(sourceEpisodes, newAnime, newSource)
        } catch (_: Exception) {
            // Worst case, chapters won't be synced
        }

        // Update chapters read, bookmark and dateFetch
        if (migrateEpisodes) {
            val prevAnimeEpisodes = getEpisodesByAnimeId.await(oldAnime.id)
            val animeEpisodes = getEpisodesByAnimeId.await(newAnime.id)

            val maxEpisodeSeen = prevAnimeEpisodes
                .filter { it.seen }
                .maxOfOrNull { it.episodeNumber }

            val updatedAnimeEpisodes = animeEpisodes.map { animeEpisode ->
                var updatedEpisode = animeEpisode
                if (updatedEpisode.isRecognizedNumber) {
                    val prevEpisode = prevAnimeEpisodes
                        .find { it.isRecognizedNumber && it.episodeNumber == updatedEpisode.episodeNumber }

                    if (prevEpisode != null) {
                        updatedEpisode = updatedEpisode.copy(
                            dateFetch = prevEpisode.dateFetch,
                            bookmark = prevEpisode.bookmark,
                        )
                    }

                    if (maxEpisodeSeen != null && updatedEpisode.episodeNumber <= maxEpisodeSeen) {
                        updatedEpisode = updatedEpisode.copy(seen = true)
                    }
                }

                updatedEpisode
            }

            val episodeUpdates = updatedAnimeEpisodes.map { it.toEpisodeUpdate() }
            updateEpisode.awaitAll(episodeUpdates)
        }

        // Update categories
        if (migrateCategories) {
            val categoryIds = getCategories.await(oldAnime.id).map { it.id }
            setAnimeCategories.await(newAnime.id, categoryIds)
        }

        // Update track
        getTracks.await(oldAnime.id).mapNotNull { track ->
            val updatedTrack = track.copy(animeId = newAnime.id)

            val service = enhancedServices
                .firstOrNull { it.isTrackFrom(updatedTrack, oldAnime, oldSource) }

            if (service != null) {
                service.migrateTrack(updatedTrack, newAnime, newSource)
            } else {
                updatedTrack
            }
        }
            .takeIf { it.isNotEmpty() }
            ?.let { insertTrack.awaitAll(it) }

        // Update custom cover (recheck if custom cover exists)
        if (migrateCustomCover && oldAnime.hasCustomCover(coverCache)) {
            coverCache.getCustomCoverFile(oldAnime.id).inputStream().use {
                coverCache.setCustomCoverToCache(newAnime, it)
            }
        }

        // Update custom background (recheck if custom background exists)
        if (migrateCustomBackground && oldAnime.hasCustomBackground(backgroundCache)) {
            backgroundCache.getCustomBackgroundFile(oldAnime.id).inputStream().use {
                backgroundCache.setCustomBackgroundToCache(newAnime, it)
            }
        }

        val addedToLibrary = updateAnime.await(
            AnimeUpdate(
                id = newAnime.id,
                favorite = true,
                episodeFlags = oldAnime.episodeFlags,
                viewerFlags = oldAnime.viewerFlags,
                dateAdded = if (replace) oldAnime.dateAdded else Instant.now().toEpochMilli(),
            ),
        )
        if (!addedToLibrary) return false
        if (replace && !updateAnime.awaitUpdateFavorite(oldAnime.id, favorite = false)) return false

        if (deleteDownloaded && oldSource != null) {
            downloadManager.deleteAnime(oldAnime, oldSource)
        }
        return true
    }

    @Immutable
    data class State(
        val isMigrating: Boolean = false,
    )
}
