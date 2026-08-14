package eu.kanade.tachiyomi.ui.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerEnumsTest {

    @Test
    fun `getDecoderFromValue falls back to software decoding for unknown values`() {
        assertEquals(Decoder.SW, getDecoderFromValue("unknown-decoder"))
    }
}
