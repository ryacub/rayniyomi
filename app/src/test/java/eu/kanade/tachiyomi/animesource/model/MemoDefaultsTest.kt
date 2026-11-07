package eu.kanade.tachiyomi.animesource.model

import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Headers
import org.junit.jupiter.api.Test

class MemoDefaultsTest {

    private val memo = JsonObject(mapOf("token" to JsonPrimitive("kept")))

    @Test
    fun `extension-lib 16 hoster constructor leaves an empty memo`() {
        val hoster = Hoster::class.java
            .getDeclaredConstructor(
                String::class.java,
                String::class.java,
                List::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType,
            )
            .newInstance("https://example.com/h", "Host", null, "", false) as Hoster

        hoster.memo shouldBe JsonObject(emptyMap())
    }

    @Test
    fun `extension-lib 16 video constructor leaves an empty memo`() {
        val video = Video::class.java
            .getDeclaredConstructor(*videoParameterTypes())
            .newInstance(
                "https://example.com/v",
                "Video",
                null,
                null,
                null,
                false,
                emptyList<Track>(),
                emptyList<Track>(),
                emptyList<TimeStamp>(),
                emptyList<Pair<String, String>>(),
                emptyList<Pair<String, String>>(),
                emptyList<Pair<String, String>>(),
                "",
                false,
            ) as Video

        video.memo shouldBe JsonObject(emptyMap())
    }

    @Test
    fun `extension-lib 16 hoster copy keeps the memo of the source`() {
        val source = Hoster(hosterName = "Host", memo = memo)
        val copy = Hoster::class.java
            .getDeclaredMethod(
                "copy",
                String::class.java,
                String::class.java,
                List::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType,
            )
            .invoke(source, source.hosterUrl, source.hosterName, null, "", true) as Hoster

        copy.memo shouldBe memo
        copy.lazy shouldBe true
    }

    @Test
    fun `extension-lib 16 video copy keeps the memo of the source`() {
        val source = Video(videoUrl = "https://example.com/v", memo = memo)
        val copy = Video::class.java
            .getDeclaredMethod("copy", *videoParameterTypes())
            .invoke(
                source,
                "https://example.com/other",
                source.videoTitle,
                source.resolution,
                source.bitrate,
                source.headers,
                source.preferred,
                source.subtitleTracks,
                source.audioTracks,
                source.timestamps,
                source.mpvArgs,
                source.ffmpegStreamArgs,
                source.ffmpegVideoArgs,
                source.internalData,
                source.initialized,
            ) as Video

        copy.videoUrl shouldBe "https://example.com/other"
        copy.memo shouldBe memo
    }

    @Test
    fun `copy keeps the memo of the source`() {
        Hoster(hosterName = "Host", memo = memo).copy(lazy = true).memo shouldBe memo
        Video(videoUrl = "https://example.com/v", memo = memo).copy(videoTitle = "V").memo shouldBe memo
    }

    private fun videoParameterTypes(): Array<Class<*>> = arrayOf(
        String::class.java,
        String::class.java,
        Int::class.javaObjectType,
        Int::class.javaObjectType,
        Headers::class.java,
        Boolean::class.javaPrimitiveType!!,
        List::class.java,
        List::class.java,
        List::class.java,
        List::class.java,
        List::class.java,
        List::class.java,
        String::class.java,
        Boolean::class.javaPrimitiveType!!,
    )
}
