package eu.kanade.presentation.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import eu.kanade.presentation.entries.components.ItemCover
import kotlinx.coroutines.withTimeoutOrNull
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import kotlin.math.roundToInt

internal val LocalLibraryCoverTransition = staticCompositionLocalOf<LibraryCoverTransition?> { null }
internal val LocalActiveCoverPage = staticCompositionLocalOf { false }
private val LocalCoverScreen = staticCompositionLocalOf<String?> { null }

internal data class LibraryCoverKey(val isManga: Boolean, val id: Long)

private data class CoverLocation(val screen: String, val key: LibraryCoverKey)

private data class CoverMove(
    val library: CoverLocation,
    val detail: CoverLocation,
)

private data class CoverFrame(val bounds: Rect, val painter: Painter)

private class CoverOverlay(val source: CoverLocation, val target: CoverLocation?, start: CoverFrame) {
    val painter = start.painter
    var bounds by mutableStateOf(start.bounds)
}

private class CoverEndpoint {
    var coordinates: LayoutCoordinates? = null
    var painter: Painter? = null
}

internal class LibraryCoverTransition {
    private val covers = mutableMapOf<CoverLocation, CoverEndpoint>()
    private var request by mutableStateOf<CoverMove?>(null)
    private var overlay by mutableStateOf<CoverOverlay?>(null)
    private var pendingStart: CoverFrame? = null
    private var previousScreen: String? = null
    private var hostCoordinates: LayoutCoordinates? = null

    fun open(navigator: Navigator, screen: Screen, key: LibraryCoverKey) {
        val library = CoverLocation(navigator.lastItem.key, key)
        val detail = CoverLocation(screen.key, key)
        pendingStart = frame(library)
        request = CoverMove(library, detail)
        navigator.push(screen)
    }

    fun matches(initial: Screen, target: Screen): Boolean {
        val move = request ?: return false
        return (initial.key == move.library.screen && target.key == move.detail.screen) ||
            (initial.key == move.detail.screen && target.key == move.library.screen)
    }

    @Composable
    fun TrackScreen(navigator: Navigator) {
        val screen = navigator.lastItem.key
        val move = request
        val interruptedFrame = remember(screen, move) {
            overlay?.takeIf { it.source == move?.library }?.let { CoverFrame(it.bounds, it.painter) }
        }
        LaunchedEffect(screen, move) {
            val previous = previousScreen
            previousScreen = screen
            when {
                move == null -> Unit
                screen == move.detail.screen && previous == move.library.screen -> {
                    val start = pendingStart
                    pendingStart = null
                    if (start != null) animate(navigator, start, move.library, move.detail, holdSource = true)
                }
                screen == move.library.screen && previous == move.detail.screen -> {
                    try {
                        frame(move.detail)?.let { detailFrame ->
                            animate(
                                navigator,
                                interruptedFrame ?: detailFrame,
                                move.detail,
                                move.library,
                                holdSource = false,
                            )
                        }
                    } finally {
                        if (request === move) request = null
                    }
                }
                navigator.items.none { it.key == move.detail.screen } -> {
                    pendingStart = null
                    request = null
                }
            }
        }
    }

    @Composable
    fun DrawOverlay(modifier: Modifier) {
        Layout(
            modifier = modifier.onGloballyPositioned { hostCoordinates = it },
            content = {
                overlay?.let {
                    Image(
                        painter = it.painter,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).testTag("library-cover-motion"),
                    )
                }
            },
        ) { measurables, constraints ->
            val bounds = overlay?.bounds
            val cover = if (bounds != null) {
                measurables.firstOrNull()?.measure(
                    Constraints.fixed(bounds.width.roundToInt(), bounds.height.roundToInt()),
                )
            } else {
                null
            }
            layout(constraints.maxWidth, constraints.maxHeight) {
                if (cover != null && bounds != null) {
                    cover.place(bounds.left.roundToInt(), bounds.top.roundToInt())
                }
            }
        }
    }

    @Composable
    fun Cover(data: Any?, modifier: Modifier, description: String, onClick: (() -> Unit)?, key: LibraryCoverKey) {
        val screen = LocalCoverScreen.current ?: return ItemCover.Book(data, modifier, description, onClick = onClick)
        val location = remember(screen, key) { CoverLocation(screen, key) }
        val endpoint = remember(location, data) { CoverEndpoint() }
        DisposableEffect(location, endpoint) {
            covers[location] = endpoint
            onDispose { covers.remove(location, endpoint) }
        }
        ItemCover.Book(
            data = data,
            modifier = modifier
                .onGloballyPositioned { endpoint.coordinates = it }
                .graphicsLayer {
                    val motion = overlay
                    alpha = if (motion?.source == location || motion?.target == location) 0f else 1f
                },
            contentDescription = description,
            onClick = onClick,
            onPainterReady = { endpoint.painter = it },
        )
    }

    private fun bounds(location: CoverLocation): Rect? {
        val coordinates = covers[location]?.coordinates ?: return null
        val host = hostCoordinates ?: return null
        if (!coordinates.isAttached || !host.isAttached) return null
        val visibleBounds = coordinates.boundsInWindow(clipBounds = true)
        if (visibleBounds.isEmpty || !visibleBounds.overlaps(host.boundsInWindow())) return null
        return host.localBoundingBoxOf(coordinates, clipBounds = false).takeUnless { it.isEmpty }
    }

    private fun frame(location: CoverLocation): CoverFrame? {
        val painter = covers[location]?.painter ?: return null
        return bounds(location)?.let { CoverFrame(it, painter) }
    }

    private suspend fun animate(
        navigator: Navigator,
        start: CoverFrame,
        source: CoverLocation,
        target: CoverLocation,
        holdSource: Boolean,
    ) {
        if (holdSource) overlay = CoverOverlay(source, target = null, start)
        try {
            withTimeoutOrNull(300) {
                do {
                    val targetBounds = withFrameNanos { bounds(target) }
                } while (targetBounds == null)
            } ?: return

            val motion = CoverOverlay(source, target, start)
            overlay = motion
            Animatable(0f).animateTo(1f, tween(durationMillis = 260)) {
                if (overlay === motion && navigator.lastItem.key == target.screen) {
                    val targetBounds = bounds(target)
                    if (targetBounds == null) {
                        overlay = null
                    } else {
                        motion.bounds = lerp(start.bounds, targetBounds, value)
                    }
                }
            }
        } finally {
            overlay = null
        }
    }
}

@Composable
internal fun LibraryCoverTransitionHost(navigator: Navigator, modifier: Modifier) {
    val transition = remember(navigator) { LibraryCoverTransition() }
    transition.TrackScreen(navigator)
    Box(modifier) {
        CompositionLocalProvider(LocalLibraryCoverTransition provides transition) {
            DefaultNavigatorScreenTransition(navigator = navigator, useFade = transition::matches) { screen ->
                CompositionLocalProvider(LocalCoverScreen provides screen.key) {
                    screen.Content()
                }
            }
        }
        transition.DrawOverlay(Modifier.matchParentSize())
    }
}

@Composable
internal fun LibraryTransitionCover(
    data: Any?,
    modifier: Modifier,
    source: Boolean,
    model: Any? = data,
    contentDescription: String = "",
    onClick: (() -> Unit)? = null,
) {
    val transition = LocalLibraryCoverTransition.current
    val key = when (data) {
        is MangaCover -> LibraryCoverKey(true, data.entryId)
        is AnimeCover -> LibraryCoverKey(false, data.entryId)
        is Manga -> LibraryCoverKey(true, data.id)
        is Anime -> LibraryCoverKey(false, data.id)
        else -> null
    }
    if (transition != null && key != null && (!source || LocalActiveCoverPage.current)) {
        transition.Cover(model, modifier, contentDescription, onClick, key)
    } else {
        ItemCover.Book(model, modifier, contentDescription, onClick = onClick)
    }
}
