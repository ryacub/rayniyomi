package tachiyomi.presentation.widget.util

import android.app.Application
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composer
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.provideContent
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageResult
import eu.kanade.tachiyomi.util.system.dpToPx
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import tachiyomi.core.common.di.GraphProvider
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.updates.manga.model.MangaUpdatesWithRelations
import tachiyomi.presentation.widget.di.WidgetGraph
import tachiyomi.presentation.widget.entries.manga.MangaUpdatesGridGlanceWidget
import java.lang.reflect.InvocationTargetException

@OptIn(ExperimentalCoroutinesApi::class, InternalComposeApi::class)
class UpdatesWidgetFlowTest {
    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `unchanged visible covers do not decode again`() = runTest {
        val updates = MutableSharedFlow<List<MangaUpdatesWithRelations>>()
        val app = mockk<Application>(moreInterfaces = arrayOf(GraphProvider::class))
        val graph = mockk<WidgetGraph> {
            every { application } returns app
            every { securityPreferences.useAuthenticator().get() } returns false
            every { getMangaUpdates.subscribe(false, any()) } returns updates
        }
        every { app.applicationContext } returns app
        @Suppress("UNCHECKED_CAST")
        val provider = app as GraphProvider<WidgetGraph>
        every { provider.graph } returns graph
        val resources = mockk<Resources>()
        every { app.resources } returns resources
        every { resources.getDimension(any()) } returns 0f
        mockkStatic("eu.kanade.tachiyomi.util.system.DensityExtensionsKt")
        every { any<Int>().dpToPx } answers { firstArg<Int>() }
        val loader = mockk<ImageLoader>()
        mockkStatic(SingletonImageLoader::class)
        every { SingletonImageLoader.get(any()) } returns loader
        coEvery { loader.execute(any()) } returns mockk<ImageResult> { every { image } returns null }
        mockkStatic("tachiyomi.core.common.util.lang.CoroutinesExtensionsKt")
        coEvery { withIOContext<Any?>(any()) } coAnswers {
            firstArg<suspend CoroutineScope.() -> Any?>().invoke(this@runTest)
        }

        val id = mockk<GlanceId>()
        mockkConstructor(GlanceAppWidgetManager::class)
        coEvery { anyConstructed<GlanceAppWidgetManager>().getGlanceIds(any<Class<GlanceAppWidget>>()) } returns
            listOf(id)
        coEvery { anyConstructed<GlanceAppWidgetManager>().getAppWidgetSizes(id) } returns listOf(DpSize(64.dp, 95.dp))
        val content = slot<@Composable () -> Unit>()
        mockkStatic("androidx.glance.appwidget.GlanceAppWidgetKt")
        coEvery { any<GlanceAppWidget>().provideContent(capture(content)) } throws CapturedContent()
        every { app.applicationContext } returns app
        try {
            MangaUpdatesGridGlanceWidget().provideGlance(app, id)
        } catch (_: CapturedContent) {
        }
        val flow = captureFlow(content.captured)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
        val update = MangaUpdatesWithRelations(
            mangaId = 1L,
            mangaTitle = "Manga",
            chapterId = 1L,
            chapterName = "Chapter 1",
            scanlator = null,
            read = false,
            bookmark = false,
            lastPageRead = 0L,
            sourceId = 1L,
            dateFetch = 1L,
            coverData = MangaCover(1L, 1L, true, "https://source.test/cover.png", 0L),
        )
        updates.emit(listOf(update))
        advanceUntilIdle()
        updates.emit(listOf(update.copy(chapterName = "Chapter 2", chapterId = 2L)))
        advanceUntilIdle()
        collector.cancel()

        coVerify(exactly = 1) { loader.execute(any()) }
    }

    private fun captureFlow(content: @Composable () -> Unit): Flow<*> {
        var captured: Flow<*>? = null
        val composer = mockk<Composer>(relaxed = true)
        every { composer.startRestartGroup(any()) } returns composer
        every { composer.shouldExecute(any(), any()) } returns true
        every { composer.rememberedValue() } returns Composer.Empty
        every { composer.updateRememberedValue(any()) } answers {
            val value = firstArg<Any?>()
            if (value is Flow<*>) {
                captured = value
                throw CapturedFlow()
            }
        }
        try {
            content.javaClass.getMethod("invoke", Composer::class.java, Int::class.javaPrimitiveType)
                .invoke(content, composer, 0)
        } catch (error: InvocationTargetException) {
            if (error.cause !is CapturedFlow) throw error.cause ?: error
        }
        assertNotNull(captured, "provideGlance must create the production updates flow")
        return captured!!
    }

    private class CapturedFlow : RuntimeException()
    private class CapturedContent : RuntimeException()
}
