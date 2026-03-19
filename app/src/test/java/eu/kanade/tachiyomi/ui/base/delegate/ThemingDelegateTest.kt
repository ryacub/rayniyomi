package eu.kanade.tachiyomi.ui.base.delegate

import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.tachiyomi.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ThemingDelegateTest {

    @Test
    fun `custom app theme resolves to base tachiyomi style`() {
        val resIds = ThemingDelegate.getThemeResIds(
            appTheme = AppTheme.CUSTOM,
            isAmoled = false,
        )

        assertEquals(listOf(R.style.Theme_Tachiyomi), resIds)
    }
}
