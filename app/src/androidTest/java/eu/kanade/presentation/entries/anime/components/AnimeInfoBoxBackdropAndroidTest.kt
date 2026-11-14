package eu.kanade.presentation.entries.anime.components

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import eu.kanade.tachiyomi.data.coil.AnimeImageFetcher
import eu.kanade.tachiyomi.data.coil.useBackground
import okhttp3.Call
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class AnimeInfoBoxBackdropAndroidTest {

    @get:Rule
    val compose = createComposeRule()

    private val requests = CopyOnWriteArrayList<Boolean>()
    private var backgroundLoads = true
    private var coverLoads = true

    private val anime = mutableStateOf(
        Anime.create().copy(
            id = 987_654L,
            title = "Jaadugar",
            thumbnailUrl = "https://example.org/poster.jpg",
            backgroundUrl = "https://example.org/background.jpg",
        ),
    )

    @Before
    fun installImageLoader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imageLoader = ImageLoader.Builder(context)
            .components {
                add(AnimeFetcherFactory(), Anime::class)
                add(AnimeCoverFetcherFactory(), AnimeCover::class)
            }
            .memoryCache(null)
            .diskCache(null)
            .build()
        SingletonImageLoader.setUnsafe(imageLoader)
    }

    @After
    fun resetImageLoader() {
        SingletonImageLoader.reset()
    }

    @Test
    fun aLoadedBackgroundNeedsNoPosterFallback() {
        show()

        awaitRequests(background = 1, cover = 1)
        settle()

        assertRequests(background = 1, cover = 1)
    }

    @Test
    fun aMissingBackgroundUrlFallsBackToThePoster() {
        anime.value = anime.value.copy(backgroundUrl = null)
        show()

        awaitRequests(background = 1, cover = 2)

        assertRequests(background = 1, cover = 2)
    }

    @Test
    fun aFailedBackgroundFallsBackToThePoster() {
        backgroundLoads = false
        show()

        awaitRequests(background = 1, cover = 2)

        assertRequests(background = 1, cover = 2)
    }

    @Test
    fun whenBothImagesFailTheBackdropStopsAndTheHeaderStaysUsable() {
        backgroundLoads = false
        coverLoads = false
        show()

        awaitRequests(background = 1, cover = 2)
        settle()

        assertRequests(background = 1, cover = 2)
        compose.onNodeWithText("Jaadugar").assertExists()
    }

    @Test
    fun aNewBackgroundIsRequestedAgainAfterAFailure() {
        backgroundLoads = false
        show()
        awaitRequests(background = 1, cover = 2)

        backgroundLoads = true
        compose.runOnIdle { anime.value = anime.value.copy(backgroundLastModified = 1L) }

        awaitRequests(background = 2, cover = 2)
        settle()
        assertEquals("background requests in $requests", 2, count(true))
    }

    private fun show() {
        compose.setContent {
            AnimeInfoBox(
                isTabletUi = false,
                appBarPadding = 0.dp,
                anime = anime.value,
                sourceName = "Anidap",
                isStubSource = false,
                onCoverClick = {},
                doSearch = { _, _ -> },
            )
        }
    }

    private fun awaitRequests(background: Int, cover: Int) {
        runCatching {
            compose.waitUntil(timeoutMillis = 5_000) {
                count(true) >= background && count(false) >= cover
            }
        }
    }

    private fun settle() {
        Thread.sleep(500)
        compose.waitForIdle()
    }

    private fun assertRequests(background: Int, cover: Int) {
        assertEquals("background requests in $requests", background, count(true))
        assertEquals("cover requests in $requests", cover, count(false))
    }

    private fun count(useBackground: Boolean) = requests.count { it == useBackground }

    private inner class RecordingFetcher(
        private val isBackground: Boolean,
        private val productionFetcher: Fetcher? = null,
    ) : Fetcher {
        override suspend fun fetch(): FetchResult? {
            requests += isBackground
            if (productionFetcher != null) return productionFetcher.fetch()
            if (isBackground && !backgroundLoads) throw IOException("background unavailable")
            if (!isBackground && !coverLoads) throw IOException("cover unavailable")
            return ImageFetchResult(
                image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImage(),
                isSampled = false,
                dataSource = DataSource.MEMORY,
            )
        }
    }

    private inner class AnimeFetcherFactory : Fetcher.Factory<Anime> {
        private val productionFactory = AnimeImageFetcher.AnimeFactory(lazy<Call.Factory> { OkHttpClient() })

        override fun create(data: Anime, options: Options, imageLoader: ImageLoader): Fetcher {
            val missingBackgroundUrl = options.useBackground && data.backgroundUrl == null
            return RecordingFetcher(
                isBackground = options.useBackground,
                productionFetcher = if (missingBackgroundUrl) {
                    productionFactory.create(
                        data,
                        options,
                        imageLoader,
                    )
                } else {
                    null
                },
            )
        }
    }

    private inner class AnimeCoverFetcherFactory : Fetcher.Factory<AnimeCover> {
        override fun create(data: AnimeCover, options: Options, imageLoader: ImageLoader): Fetcher =
            RecordingFetcher(isBackground = false)
    }
}
