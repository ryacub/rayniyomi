package eu.kanade.tachiyomi.source.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test

/**
 * An extension built against a newer SManga links against members this app
 * version did not ship. Issue #979: the extension called getMemo(). Issue
 * #1253: lib-1.6 extensions also call setMemo() and read the value back later
 * in getMangaUrl(). This test resolves each member the way extension bytecode
 * does at run time.
 */
class SMangaExtensionAbiTest {

    private val memo = JsonObject(mapOf("slug" to JsonPrimitive("some-series-1a2b")))

    @Test
    fun `an extension compiled against a newer SManga resolves getMemo`() {
        val method = SManga::class.java.getMethod("getMemo")

        method.returnType shouldBe JsonObject::class.java
        method.invoke(SManga.create()) shouldBe JsonObject(emptyMap())
    }

    @Test
    fun `an extension compiled against a newer SManga resolves setMemo`() {
        val setter = SManga::class.java.getMethod("setMemo", JsonObject::class.java)
        setter.returnType shouldBe java.lang.Void.TYPE

        val manga = SManga.create()
        setter.invoke(manga, memo)

        manga.memo shouldBe memo
    }

    @Test
    fun `copy keeps the memo`() {
        val manga = SManga.create().apply {
            url = "/series/1"
            title = "Series"
            memo = this@SMangaExtensionAbiTest.memo
        }

        manga.copy().memo shouldBe memo
    }
}
