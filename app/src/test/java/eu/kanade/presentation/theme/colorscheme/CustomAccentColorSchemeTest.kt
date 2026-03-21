package eu.kanade.presentation.theme.colorscheme

import android.content.Context
import androidx.compose.ui.graphics.Color
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomAccentColorSchemeTest {

    private val context: Context = mockk(relaxed = true)

    @Test
    fun `same seed produces deterministic light scheme`() {
        val first = CustomAccentColorScheme(context = context, seed = 0xFF4285F4.toInt()).lightScheme
        val second = CustomAccentColorScheme(context = context, seed = 0xFF4285F4.toInt()).lightScheme

        assertEquals(first.primary, second.primary)
        assertEquals(first.onPrimary, second.onPrimary)
        assertEquals(first.surface, second.surface)
        assertEquals(first.onSurface, second.onSurface)
    }

    @Test
    fun `seed alpha is normalized before generation`() {
        val transparentSeed = CustomAccentColorScheme(context = context, seed = 0x004285F4).lightScheme
        val opaqueSeed = CustomAccentColorScheme(context = context, seed = 0xFF4285F4.toInt()).lightScheme

        assertEquals(transparentSeed.primary, opaqueSeed.primary)
        assertEquals(transparentSeed.onPrimary, opaqueSeed.onPrimary)
    }

    @Test
    fun `tertiary container uses container token`() {
        val scheme = CustomAccentColorScheme(context = context, seed = 0xFF4285F4.toInt()).lightScheme

        assertNotEquals(scheme.tertiary, scheme.tertiaryContainer)
    }

    @Test
    fun `contrast resolver honors android 14 system contrast`() {
        assertEquals(0.6, resolveCustomSchemeContrast(sdkInt = 34, uiModeContrast = 0.6f), 0.000001)
        assertEquals(0.0, resolveCustomSchemeContrast(sdkInt = 34, uiModeContrast = null))
        assertEquals(0.0, resolveCustomSchemeContrast(sdkInt = 33, uiModeContrast = 1.0f))
    }

    @Test
    fun `contrast clamp improves low contrast pairs`() {
        val source = TachiyomiColorScheme.lightScheme.copy(
            primary = Color(0xFF777777),
            onPrimary = Color(0xFF787878),
        )

        val before = contrastRatio(source.primary, source.onPrimary)
        val clamped = clampOnColorsForContrast(source)
        val after = contrastRatio(clamped.primary, clamped.onPrimary)

        assertTrue(after > before)
        assertTrue(after >= 4.5)
    }

    @Test
    fun `fallback is used when scheme remains unreadable after clamp`() {
        val unreadable = TachiyomiColorScheme.lightScheme.copy(
            primary = Color(0x804285F4),
            onPrimary = Color.White,
        )

        val resolved = ensureReadableOrFallback(
            source = unreadable,
            fallback = TachiyomiColorScheme.lightScheme,
        )

        assertEquals(TachiyomiColorScheme.lightScheme, resolved)
    }

    @Test
    fun `contrast warning maps adjusted modes deterministically`() {
        val warning = contrastWarningFromAdjustments(
            lightAdjusted = true,
            darkAdjusted = false,
            darkAmoledAdjusted = true,
        )

        assertEquals(
            setOf(CustomAccentContrastMode.LIGHT, CustomAccentContrastMode.DARK_AMOLED),
            warning.failingModes,
        )
        assertTrue(warning.hasWarning)
    }

    @Test
    fun `contrast warning is empty when no mode is adjusted`() {
        val warning = contrastWarningFromAdjustments(
            lightAdjusted = false,
            darkAdjusted = false,
            darkAmoledAdjusted = false,
        )

        assertTrue(warning.failingModes.isEmpty())
        assertTrue(!warning.hasWarning)
    }
}
