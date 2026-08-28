package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import rx.Observable
import kotlin.coroutines.Continuation

/**
 * An extension compiled against a newer MangaSource calls the members
 * this app version must still ship: the default methods it relies on and
 * the suspend methods it overrides. A dropped member fails at first call
 * with NoSuchMethodError. A suspend method must also keep its trailing
 * Continuation parameter, or callers cannot resume it. This test resolves
 * each member the way extension bytecode does at run time.
 */
class MangaSourceExtensionAbiTest {

    @Test
    fun `default methods resolve as real methods on the interface class`() {
        MangaSource::class.java.getMethod("getLang").let {
            it.returnType shouldBe String::class.java
            it.isDefault shouldBe true
        }
        MangaSource::class.java.getMethod("getFilterList").let {
            it.returnType shouldBe FilterList::class.java
            it.isDefault shouldBe true
        }
        MangaSource::class.java.getMethod("fetchMangaDetails", SManga::class.java).let {
            it.returnType shouldBe Observable::class.java
            it.isDefault shouldBe true
        }
        MangaSource::class.java.getMethod("fetchChapterList", SManga::class.java).let {
            it.returnType shouldBe Observable::class.java
            it.isDefault shouldBe true
        }
        MangaSource::class.java.getMethod("fetchPageList", SChapter::class.java).let {
            it.returnType shouldBe Observable::class.java
            it.isDefault shouldBe true
        }
    }

    @Test
    fun `suspend methods end in a Continuation parameter`() {
        suspendMethod("getPopularManga", Int::class.java)
        suspendMethod("getLatestUpdates", Int::class.java)
        suspendMethod("getSearchManga", Int::class.java, String::class.java, FilterList::class.java)
        suspendMethod(
            "getMangaUpdate",
            SManga::class.java,
            List::class.java,
            Boolean::class.java,
            Boolean::class.java,
        )
        suspendMethod("getPageList", SChapter::class.java)
    }

    private fun suspendMethod(name: String, vararg params: Class<*>) {
        val method = MangaSource::class.java.getMethod(name, *params, Continuation::class.java)
        method.parameterTypes.last() shouldBe Continuation::class.java
        method.returnType shouldBe Any::class.java
    }
}
