package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class UiPreferences(
    private val preferenceStore: PreferenceStore,
) {

    companion object {
        const val CUSTOM_THEME_ACCENT_SEED_UNSET = Int.MIN_VALUE
        private const val CUSTOM_THEME_RECENT_ACCENT_LIMIT = 5
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }

        internal fun normalizeRecentAccentSeeds(seeds: List<Int>): List<Int> {
            return seeds
                .asSequence()
                .map { it or 0xFF000000.toInt() }
                .filter { it != CUSTOM_THEME_ACCENT_SEED_UNSET }
                .distinct()
                .take(CUSTOM_THEME_RECENT_ACCENT_LIMIT)
                .toList()
        }

        internal fun upsertRecentAccentSeed(
            existing: List<Int>,
            appliedSeed: Int,
        ): List<Int> {
            if (appliedSeed == CUSTOM_THEME_ACCENT_SEED_UNSET) return normalizeRecentAccentSeeds(existing)
            val normalizedApplied = appliedSeed or 0xFF000000.toInt()
            return normalizeRecentAccentSeeds(listOf(normalizedApplied) + existing)
        }
    }

    fun themeMode() = preferenceStore.getEnum("pref_theme_mode_key", ThemeMode.SYSTEM)

    fun appTheme() = preferenceStore.getEnum(
        "pref_app_theme",
        if (DeviceUtil.isDynamicColorAvailable) {
            AppTheme.MONET
        } else {
            AppTheme.DEFAULT
        },
    )

    fun themeDarkAmoled() = preferenceStore.getBoolean("pref_theme_dark_amoled_key", false)
    fun dynamicEntryCoverTheming() = preferenceStore.getBoolean("pref_dynamic_entry_cover_theming", false)
    fun customThemeAccentSeed() = preferenceStore.getInt(
        "pref_custom_theme_accent_seed",
        CUSTOM_THEME_ACCENT_SEED_UNSET,
    )

    fun customThemeRecentAccentSeeds() = preferenceStore.getObject(
        key = "pref_custom_theme_recent_accent_seeds",
        defaultValue = emptyList(),
        serializer = { seeds ->
            normalizeRecentAccentSeeds(seeds).joinToString(separator = ",") { normalized ->
                normalized.toUInt().toString(16).padStart(8, '0')
            }
        },
        deserializer = { encoded ->
            if (encoded.isBlank()) {
                emptyList()
            } else {
                normalizeRecentAccentSeeds(
                    encoded.split(",")
                        .mapNotNull { token ->
                            token.trim()
                                .takeIf { it.isNotEmpty() }
                                ?.toUIntOrNull(16)
                                ?.toInt()
                        },
                )
            }
        },
    )

    fun addCustomThemeRecentAccentSeed(seed: Int) {
        val recentsPref = customThemeRecentAccentSeeds()
        recentsPref.set(
            upsertRecentAccentSeed(
                existing = recentsPref.get(),
                appliedSeed = seed,
            ),
        )
    }

    fun relativeTime() = preferenceStore.getBoolean("relative_time_v2", true)

    fun dateFormat() = preferenceStore.getString("app_date_format", "")

    fun tabletUiMode() = preferenceStore.getEnum("tablet_ui_mode", TabletUiMode.AUTOMATIC)

    fun startScreen() = preferenceStore.getEnum("start_screen", StartScreen.ANIME)

    fun navStyle() = preferenceStore.getEnum("bottom_rail_nav_style", NavStyle.MOVE_HISTORY_TO_MORE)
}
