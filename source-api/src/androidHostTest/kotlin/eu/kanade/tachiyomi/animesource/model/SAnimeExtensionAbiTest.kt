package eu.kanade.tachiyomi.animesource.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * An extension compiled against a newer SAnime calls the property
 * accessors, copy(), and create() through invokeinterface on the
 * companion. If this app version dropped a member, Android raises
 * NoSuchMethodError and the details fetch stays broken. This test
 * resolves each member the way extension bytecode does at run time.
 */
class SAnimeExtensionAbiTest {

    @Test
    fun `property getters resolve with exact JVM names and return types`() {
        SAnime::class.java.getMethod("getUrl").returnType shouldBe String::class.java
        SAnime::class.java.getMethod("getTitle").returnType shouldBe String::class.java
        SAnime::class.java.getMethod("getArtist").returnType shouldBe String::class.java
        SAnime::class.java.getMethod("getAuthor").returnType shouldBe String::class.java
        SAnime::class.java.getMethod("getDescription").returnType shouldBe String::class.java
        SAnime::class.java.getMethod("getGenre").returnType shouldBe String::class.java
        SAnime::class.java.getMethod("getStatus").returnType shouldBe Int::class.java
        SAnime::class.java.getMethod("getThumbnail_url").returnType shouldBe String::class.java
        SAnime::class.java.getMethod("getBackground_url").returnType shouldBe String::class.java
        SAnime::class.java.getMethod("getUpdate_strategy").returnType shouldBe AnimeUpdateStrategy::class.java
        SAnime::class.java.getMethod("getFetch_type").returnType shouldBe FetchType::class.java
        SAnime::class.java.getMethod("getSeason_number").returnType shouldBe Double::class.java
        SAnime::class.java.getMethod("getInitialized").returnType shouldBe Boolean::class.java
    }

    @Test
    fun `property setters resolve with exact JVM names and parameter types`() {
        SAnime::class.java.getMethod("setUrl", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SAnime::class.java.getMethod("setTitle", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SAnime::class.java.getMethod("setArtist", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SAnime::class.java.getMethod("setAuthor", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SAnime::class.java.getMethod("setDescription", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SAnime::class.java.getMethod("setGenre", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SAnime::class.java.getMethod("setStatus", Int::class.java).returnType shouldBe java.lang.Void.TYPE
        SAnime::class.java.getMethod("setThumbnail_url", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SAnime::class.java.getMethod("setBackground_url", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SAnime::class.java.getMethod("setUpdate_strategy", AnimeUpdateStrategy::class.java)
            .returnType shouldBe java.lang.Void.TYPE
        SAnime::class.java.getMethod("setFetch_type", FetchType::class.java)
            .returnType shouldBe java.lang.Void.TYPE
        SAnime::class.java.getMethod("setSeason_number", Double::class.java).returnType shouldBe java.lang.Void.TYPE
        SAnime::class.java.getMethod("setInitialized", Boolean::class.java).returnType shouldBe java.lang.Void.TYPE
    }

    @Test
    fun `create resolves on the companion and builds an anime`() {
        val create = SAnime.Companion::class.java.getMethod("create")
        create.returnType shouldBe SAnime::class.java

        create.invoke(SAnime.Companion).shouldBeInstanceOf<SAnime>()
    }

    @Test
    fun `copy resolves and clones an instance`() {
        val copy = SAnime::class.java.getMethod("copy")
        copy.returnType shouldBe SAnime::class.java
        copy.isDefault shouldBe true

        val original = SAnime.create().apply {
            url = "https://example.com/anime/1"
            title = "Original"
        }
        copy.invoke(original).shouldBeInstanceOf<SAnime>().title shouldBe "Original"
    }
}
