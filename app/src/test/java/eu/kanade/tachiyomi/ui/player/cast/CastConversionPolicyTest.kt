package eu.kanade.tachiyomi.ui.player.cast

import eu.kanade.tachiyomi.ui.player.settings.CastConversionPolicy
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CastConversionPolicyTest {

    @Test
    fun `ask policy prompts before conversion`() {
        assertTrue(CastConversionPolicy.ASK.requiresPrompt())
    }

    @Test
    fun `always policy skips conversion prompt`() {
        assertFalse(CastConversionPolicy.ALWAYS.requiresPrompt())
    }

    @Test
    fun `conversion uses codec copy and removes unsupported tracks`() {
        assertArrayEquals(
            arrayOf(
                "-y", "-i", "input.mkv", "-map", "0:v:0", "-map", "0:a:0?",
                "-c", "copy", "-sn", "-dn", "-movflags", "+faststart", "-f", "mp4", "output.mp4.part",
            ),
            CastVideoConverter.buildArguments("input.mkv", "output.mp4.part"),
        )
    }
}
