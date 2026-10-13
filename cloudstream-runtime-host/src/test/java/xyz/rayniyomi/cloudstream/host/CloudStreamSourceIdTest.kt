package xyz.rayniyomi.cloudstream.host

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.security.MessageDigest

class CloudStreamSourceIdTest {

    private fun nativeId(name: String, lang: String): Long {
        val bytes = MessageDigest.getInstance("MD5").digest("${name.lowercase()}/$lang/1".toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }.reduce(Long::or) and Long.MAX_VALUE
    }

    @Test
    fun `the id is stable for the same plugin and provider`() {
        CloudStreamSourceId.of("Animewitcher", "AnimeWitcher", "ar") shouldBe
            CloudStreamSourceId.of("Animewitcher", "AnimeWitcher", "ar")
    }

    @Test
    fun `the id is positive`() {
        (CloudStreamSourceId.of("Animewitcher", "AnimeWitcher", "ar") > 0) shouldBe true
    }

    @Test
    fun `providers from different plugins get different ids`() {
        CloudStreamSourceId.of("Animewitcher", "AnimeWitcher", "ar") shouldNotBe
            CloudStreamSourceId.of("Animewitcher2", "AnimeWitcher", "ar")
    }

    @Test
    fun `the id differs from a native source with the same name and language`() {
        CloudStreamSourceId.of("Animewitcher", "AnimeWitcher", "ar") shouldNotBe nativeId("AnimeWitcher", "ar")
    }
}
