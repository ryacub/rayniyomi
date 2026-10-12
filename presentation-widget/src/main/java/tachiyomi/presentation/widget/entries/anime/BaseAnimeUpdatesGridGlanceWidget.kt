package tachiyomi.presentation.widget.entries.anime

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.unit.ColorProvider
import tachiyomi.core.common.di.metroGraph
import tachiyomi.presentation.widget.components.anime.LockedAnimeWidget
import tachiyomi.presentation.widget.components.anime.UpdatesAnimeWidget
import tachiyomi.presentation.widget.di.WidgetGraph
import tachiyomi.presentation.widget.util.appWidgetBackgroundRadius
import tachiyomi.presentation.widget.util.calculateRowAndColumnCount
import tachiyomi.presentation.widget.util.decodeVisibleCovers
import tachiyomi.presentation.widget.util.decodeWidgetCover
import java.time.Instant
import java.time.ZonedDateTime

abstract class BaseAnimeUpdatesGridGlanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    abstract val foreground: ColorProvider
    abstract val background: ImageProvider
    abstract val topPadding: Dp
    abstract val bottomPadding: Dp

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = context.metroGraph<WidgetGraph>()
        val locked = graph.securityPreferences.useAuthenticator().get()
        val containerModifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .appWidgetBackground()
            .padding(top = topPadding, bottom = bottomPadding)
            .appWidgetBackgroundRadius()

        val manager = GlanceAppWidgetManager(context)
        val ids = manager.getGlanceIds(javaClass)
        val (rowCount, columnCount) = ids
            .flatMap { manager.getAppWidgetSizes(it) }
            .maxBy { it.height.value * it.width.value }
            .calculateRowAndColumnCount(topPadding, bottomPadding)

        provideContent {
            // If app lock enabled, don't do anything
            if (locked) {
                LockedAnimeWidget(
                    foreground = foreground,
                    modifier = containerModifier,
                )
                return@provideContent
            }

            val flow = remember {
                graph.getAnimeUpdates
                    .subscribe(false, DateLimit.toEpochMilli())
                    .decodeVisibleCovers(
                        limit = rowCount * columnCount,
                        entryId = { it.animeId },
                        cover = { it.coverData.copy(isAnimeFavorite = true) },
                        decode = { graph.application.decodeWidgetCover(it) },
                    )
            }
            val data by flow.collectAsState(initial = null)
            UpdatesAnimeWidget(
                data = data,
                contentColor = foreground,
                topPadding = topPadding,
                bottomPadding = bottomPadding,
                modifier = containerModifier,
            )
        }
    }

    companion object {
        val DateLimit: Instant
            get() = ZonedDateTime.now().minusMonths(3).toInstant()
    }
}
