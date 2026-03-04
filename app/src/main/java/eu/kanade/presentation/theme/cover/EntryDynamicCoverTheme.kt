package eu.kanade.presentation.theme.cover

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun EntryDynamicCoverTheme(
    enabled: Boolean,
    coverData: Any?,
    cacheKey: String?,
    content: @Composable () -> Unit,
) {
    if (!enabled || coverData == null || cacheKey.isNullOrBlank()) {
        content()
        return
    }

    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val currentScheme = MaterialTheme.colorScheme
    val tokensState = produceState<CoverThemeTokens?>(
        initialValue = null,
        coverData,
        cacheKey,
        isDark,
        enabled,
    ) {
        value = CoverThemePaletteService.tokensFor(
            context = context,
            data = coverData,
            cacheKey = cacheKey,
            isDark = isDark,
        )
    }

    val themedScheme = remember(currentScheme, tokensState.value) {
        tokensState.value?.applyTo(currentScheme) ?: currentScheme
    }

    MaterialTheme(
        colorScheme = themedScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        content()
    }
}
