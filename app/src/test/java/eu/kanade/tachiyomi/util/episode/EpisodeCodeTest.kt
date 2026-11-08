package eu.kanade.tachiyomi.util.episode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeCodeTest {

    @Test
    fun `bracketed code drops its brackets`() {
        val name = "[S04-E24] That Time I Got Reincarnated as a Slime [1080p] [Dual Audio] [Multi Sub] " +
            "[10Bit] [HEVC] [AAC].mkv"

        assertEquals("S04-E24", episodeCode(name))
    }

    @Test
    fun `release group tag before the code is skipped`() {
        assertEquals("S02E05", episodeCode("[SubsPlease] Slime - S02E05 [1080p].mkv"))
    }

    @Test
    fun `code match ignores case`() {
        assertEquals("s02e05", episodeCode("slime s02e05.mp4"))
    }

    @Test
    fun `code may have a space between season and episode`() {
        assertEquals("S1 E3", episodeCode("Slime S1 E3 The Battle"))
    }

    @Test
    fun `name without a code has none`() {
        assertNull(episodeCode("The Final Battle"))
    }

    @Test
    fun `letters inside a word are not a code`() {
        assertNull(episodeCode("Dress02E05"))
    }
}
