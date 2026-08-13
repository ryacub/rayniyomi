package eu.kanade.tachiyomi.ui.browse.manga.source.browse

import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.util.getFilterListOrNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * Regression cover for Crashlytics 4a286b9c92947650ad048e0ec6ac704e, where a defective extension
 * raised NoSuchMethodError from getFilterList and killed the process on browse screen entry.
 */
class BrowseMangaSourceFilterGuardTest {

    @Test
    fun `browse listing survives an extension that fails to link`() {
        val source = mockk<CatalogueSource> {
            every { name } returns "Broken"
            every { getFilterList() } throws NoSuchMethodError(
                "No static method runBlockingK(Lkotlin/coroutines/CoroutineContext;" +
                    "Lkotlin/jvm/functions/Function2;)Ljava/lang/Object; in class " +
                    "Lkotlinx/coroutines/BuildersKt;",
            )
        }

        val filters = source.getFilterListOrNull()

        filters shouldBe null
    }

    @Test
    fun `state records the failure so the screen can report it`() {
        val brokenSource = mockk<CatalogueSource> {
            every { name } returns "Broken"
            every { getFilterList() } throws LinkageError("incompatible host API")
        }

        val state = BrowseMangaSourceScreenModel.State(
            listing = BrowseMangaSourceScreenModel.Listing.Popular,
            filtersFailed = brokenSource.getFilterListOrNull() == null,
        )

        state.filtersFailed shouldBe true
        state.filters.isEmpty() shouldBe true
    }

    @Test
    fun `state stays clean for a working extension`() {
        val state = BrowseMangaSourceScreenModel.State(
            listing = BrowseMangaSourceScreenModel.Listing.Popular,
        )

        state.filtersFailed shouldBe false
    }
}
