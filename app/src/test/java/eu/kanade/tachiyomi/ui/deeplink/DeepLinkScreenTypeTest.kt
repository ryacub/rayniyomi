package eu.kanade.tachiyomi.ui.deeplink

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeepLinkScreenTypeTest {

    @Test
    fun `fromIntentExtra keeps missing and blank values on anime default`() {
        assertEquals(DeepLinkScreenType.ANIME, DeepLinkScreenType.fromIntentExtra(null))
        assertEquals(DeepLinkScreenType.ANIME, DeepLinkScreenType.fromIntentExtra(""))
        assertEquals(DeepLinkScreenType.ANIME, DeepLinkScreenType.fromIntentExtra("   "))
    }

    @Test
    fun `fromIntentExtra routes valid values unchanged`() {
        assertEquals(DeepLinkScreenType.ANIME, DeepLinkScreenType.fromIntentExtra("ANIME"))
        assertEquals(DeepLinkScreenType.MANGA, DeepLinkScreenType.fromIntentExtra("MANGA"))
    }

    @Test
    fun `fromIntentExtra falls back instead of throwing for malformed values`() {
        assertEquals(DeepLinkScreenType.ANIME, DeepLinkScreenType.fromIntentExtra("INVALID"))
    }
}
