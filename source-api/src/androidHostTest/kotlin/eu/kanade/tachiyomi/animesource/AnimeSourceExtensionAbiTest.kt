package eu.kanade.tachiyomi.animesource

import eu.kanade.tachiyomi.animesource.model.Hoster
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import rx.Observable
import kotlin.coroutines.Continuation

/**
 * An extension compiled against a newer AnimeSource calls the members
 * this app version must still ship: the default methods it relies on and
 * the suspend methods it overrides. A dropped member fails at first call
 * with NoSuchMethodError. A suspend method must also keep its trailing
 * Continuation parameter, or callers cannot resume it. This test resolves
 * each member the way extension bytecode does at run time.
 */
class AnimeSourceExtensionAbiTest {

    @Test
    fun `default methods resolve as real methods on the interface class`() {
        AnimeSource::class.java.getMethod("getLang").let {
            it.returnType shouldBe String::class.java
            it.isDefault shouldBe true
        }
        AnimeSource::class.java.getMethod("fetchAnimeDetails", SAnime::class.java).let {
            it.returnType shouldBe Observable::class.java
            it.isDefault shouldBe true
        }
        AnimeSource::class.java.getMethod("fetchEpisodeList", SAnime::class.java).let {
            it.returnType shouldBe Observable::class.java
            it.isDefault shouldBe true
        }
        AnimeSource::class.java.getMethod("fetchVideoList", SEpisode::class.java).let {
            it.returnType shouldBe Observable::class.java
            it.isDefault shouldBe true
        }
        AnimeSource::class.java.getMethod("getAnimeDetails", SAnime::class.java, Continuation::class.java).let {
            it.isDefault shouldBe true
        }
        AnimeSource::class.java.getMethod("getEpisodeList", SAnime::class.java, Continuation::class.java).let {
            it.isDefault shouldBe true
        }
        AnimeSource::class.java.getMethod("getHosterList", SEpisode::class.java, Continuation::class.java).let {
            it.isDefault shouldBe true
        }
        AnimeSource::class.java.getMethod("getVideoList", Hoster::class.java, Continuation::class.java).let {
            it.isDefault shouldBe true
        }
        AnimeSource::class.java.getMethod("getVideoList", SEpisode::class.java, Continuation::class.java).let {
            it.isDefault shouldBe true
        }
    }

    @Test
    fun `suspend methods end in a Continuation parameter`() {
        suspendMethod("getAnimeDetails", SAnime::class.java)
        suspendMethod("getEpisodeList", SAnime::class.java)
        suspendMethod("getSeasonList", SAnime::class.java)
        suspendMethod("getHosterList", SEpisode::class.java)
        suspendMethod("getVideoList", Hoster::class.java)
        suspendMethod("getVideoList", SEpisode::class.java)
    }

    private fun suspendMethod(name: String, vararg params: Class<*>) {
        val method = AnimeSource::class.java.getMethod(name, *params, Continuation::class.java)
        method.parameterTypes.last() shouldBe Continuation::class.java
        method.returnType shouldBe Any::class.java
    }
}
