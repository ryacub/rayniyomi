package eu.kanade.tachiyomi.source.model

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test

/**
 * An extension compiled against a newer SChapter calls the property
 * accessors, copyFrom(), and create() through invokeinterface on the
 * companion. If this app version dropped a member, Android raises
 * NoSuchMethodError and the chapter fetch stays broken. This test
 * resolves each member the way extension bytecode does at run time.
 */
class SChapterExtensionAbiTest {

    @Test
    fun `property getters resolve with exact JVM names and return types`() {
        SChapter::class.java.getMethod("getUrl").returnType shouldBe String::class.java
        SChapter::class.java.getMethod("getName").returnType shouldBe String::class.java
        SChapter::class.java.getMethod("getDate_upload").returnType shouldBe Long::class.java
        SChapter::class.java.getMethod("getChapter_number").returnType shouldBe Float::class.java
        SChapter::class.java.getMethod("getScanlator").returnType shouldBe String::class.java
    }

    @Test
    fun `property setters resolve with exact JVM names and parameter types`() {
        SChapter::class.java.getMethod("setUrl", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SChapter::class.java.getMethod("setName", String::class.java).returnType shouldBe java.lang.Void.TYPE
        SChapter::class.java.getMethod("setDate_upload", Long::class.java).returnType shouldBe java.lang.Void.TYPE
        SChapter::class.java.getMethod("setChapter_number", Float::class.java).returnType shouldBe java.lang.Void.TYPE
        SChapter::class.java.getMethod("setScanlator", String::class.java).returnType shouldBe java.lang.Void.TYPE
    }

    @Test
    fun `create resolves on the companion and builds a chapter`() {
        val create = SChapter.Companion::class.java.getMethod("create")
        create.returnType shouldBe SChapter::class.java

        create.invoke(SChapter.Companion).shouldBeInstanceOf<SChapter>()
    }

    @Test
    fun `copyFrom resolves and copies between instances`() {
        val copyFrom = SChapter::class.java.getMethod("copyFrom", SChapter::class.java)
        copyFrom.returnType shouldBe java.lang.Void.TYPE
        copyFrom.isDefault shouldBe true

        val source = SChapter.create().apply {
            url = "https://example.com/ch/1"
            name = "Chapter 1"
        }
        val target = SChapter.create()
        copyFrom.invoke(target, source)
        target.url shouldBe source.url
        target.name shouldBe source.name
    }
}
