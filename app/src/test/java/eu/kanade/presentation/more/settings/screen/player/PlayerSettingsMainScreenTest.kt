package eu.kanade.presentation.more.settings.screen.player

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.ObjectOutputStream

class PlayerSettingsMainScreenTest {

    @Test
    fun `player settings main screen serializes without its item catalog`() {
        assertDoesNotThrow {
            ObjectOutputStream(ByteArrayOutputStream()).use { output ->
                output.writeObject(PlayerSettingsMainScreen(mainSettings = true))
            }
        }
    }
}
