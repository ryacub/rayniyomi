package eu.kanade.tachiyomi.animesource.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * An extension compiled against a newer SEpisode calls the property
 * accessors, copyFrom(), and create() through invokeinterface on the
 * companion. If this app version dropped a member, Android raises
 * NoSuchMethodError and the episode fetch stays broken. This test
 * resolves each member the way extension bytecode does at run time.
 */
class SEpisodeExtensionAbiTest {

    @Test
    fun `property getters resolve with exact JVM names and return types`() {
        SEpisode::class.java.getMethod("getUrl").returnType shouldBe String::class.java
        SEpisode::class.java.getMethod("getName").returnType shouldBe String::class.java
        SEpisode::class.java.getMethod("getDate_upload").returnType shouldBe Long::class.java
        SEpisode::class.java.getMethod("getEpisode_number").returnType shouldBe Float::class.java
        SEpisode::class.java.getMethod("getFillermark").returnType shouldBe Boolean::class.java
        SEpisode::class.java.getMethod("getScanlator").returnType shouldBe String::class.java
        SEpisode::class.java.getMethod("getSummary").returnType shouldBe String::class.java
        SEpisode::class.java.getMethod("getPreview_url").returnType shouldBe String::class.java
    }

    @Test
    fun `property setters resolve with exact JVM names and parameter types`() {
        SEpisode::class.java.getMethod("setUrl", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SEpisode::class.java.getMethod("setName", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SEpisode::class.java.getMethod("setDate_upload", Long::class.java).returnType shouldBe java.lang.Void.TYPE
        SEpisode::class.java.getMethod("setEpisode_number", Float::class.java).returnType shouldBe java.lang.Void.TYPE
        SEpisode::class.java.getMethod("setFillermark", Boolean::class.java).returnType shouldBe java.lang.Void.TYPE
        SEpisode::class.java.getMethod("setScanlator", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SEpisode::class.java.getMethod("setSummary", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SEpisode::class.java.getMethod("setPreview_url", String::class.java).returnType shouldBe java.lang.Void.TYPE
    }

    @Test
    fun `create resolves on the companion and builds an episode`() {
        val create = SEpisode.Companion::class.java.getMethod("create")
        create.returnType shouldBe SEpisode::class.java

        create.invoke(SEpisode.Companion).shouldBeInstanceOf<SEpisode>()
    }

    @Test
    fun `copyFrom resolves and copies between instances`() {
        val copyFrom = SEpisode::class.java.getMethod("copyFrom", SEpisode::class.java)
        copyFrom.returnType shouldBe java.lang.Void.TYPE
        copyFrom.isDefault shouldBe true

        val source = SEpisode.create().apply {
            url = "https://example.com/ep/1"
            name = "Episode 1"
        }
        val target = SEpisode.create()
        copyFrom.invoke(target, source)
        target.url shouldBe source.url
        target.name shouldBe source.name
    }
}
