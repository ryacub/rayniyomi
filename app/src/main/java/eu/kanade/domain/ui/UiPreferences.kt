package eu.kanade.domain.ui

import eu.kanade.domain.ui.model.AppTheme
import eu.kanade.domain.ui.model.NavStyle
import eu.kanade.domain.ui.model.StartScreen
import eu.kanade.domain.ui.model.TabletUiMode
import eu.kanade.domain.ui.model.ThemeMode
import eu.kanade.tachiyomi.util.system.DeviceUtil
import eu.kanade.tachiyomi.util.system.isDynamicColorAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import tachiyomi.core.common.preference.Preference
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
        private const val PREF_CUSTOM_THEME_ACCENT_SEED = "pref_custom_theme_accent_seed"
        private const val PREF_CUSTOM_THEME_ACCENT_SCHEMA = "pref_custom_theme_accent_schema"
        private const val PREF_CUSTOM_THEME_RECENT_ACCENT_SEEDS = "pref_custom_theme_recent_accent_seeds"
        private const val MAX_RECENT_CUSTOM_THEME_ACCENT_SEEDS = 5
        private const val OPAQUE_ALPHA_MASK = 0xFF000000.toInt()
        private const val CUSTOM_THEME_ACCENT_SCHEMA_VERSION = 1
        fun dateFormat(format: String): DateTimeFormatter = when (format) {
            "" -> DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)
            else -> DateTimeFormatter.ofPattern(format, Locale.getDefault())
        }

        internal fun normalizeRecentAccentSeeds(seeds: List<Int>): List<Int> {
            return seeds
                .asSequence()
                .map { it or OPAQUE_ALPHA_MASK }
                .filter { it != CUSTOM_THEME_ACCENT_SEED_UNSET }
                .distinct()
                .take(MAX_RECENT_CUSTOM_THEME_ACCENT_SEEDS)
                .toList()
        }

        private fun normalizeAccentSeed(seed: Int): Int {
            return if (seed == CUSTOM_THEME_ACCENT_SEED_UNSET) {
                CUSTOM_THEME_ACCENT_SEED_UNSET
            } else {
                seed or OPAQUE_ALPHA_MASK
            }
        }

        internal fun upsertRecentAccentSeed(
            existing: List<Int>,
            appliedSeed: Int,
        ): List<Int> {
            if (appliedSeed == CUSTOM_THEME_ACCENT_SEED_UNSET) return normalizeRecentAccentSeeds(existing)
            val normalizedApplied = appliedSeed or OPAQUE_ALPHA_MASK
            return normalizeRecentAccentSeeds(listOf(normalizedApplied) + existing)
        }
    }

    private val customThemeAccentSeedPreference by lazy { CustomThemeAccentSeedPreference() }

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
    fun customThemeAccentSeed(): Preference<Int> = customThemeAccentSeedPreference

    fun customThemeRecentAccentSeeds() = preferenceStore.getObject(
        key = PREF_CUSTOM_THEME_RECENT_ACCENT_SEEDS,
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

    private enum class AccentSchemaState {
        VALID,
        UNKNOWN_VERSION,
        MALFORMED,
    }

    private data class CustomAccentSchemaPayload(
        val state: AccentSchemaState,
        val seed: Int?,
    ) {
        companion object {
            fun fromPersisted(raw: String): CustomAccentSchemaPayload {
                val trimmed = raw.trim()
                val separator = trimmed.indexOf(':')
                if (!trimmed.startsWith("v") || separator <= 1 || separator >= trimmed.lastIndex) {
                    return CustomAccentSchemaPayload(
                        state = AccentSchemaState.MALFORMED,
                        seed = null,
                    )
                }

                val version = trimmed.substring(1, separator).toIntOrNull()
                    ?: return CustomAccentSchemaPayload(
                        state = AccentSchemaState.MALFORMED,
                        seed = null,
                    )
                val token = trimmed.substring(separator + 1).trim()
                if (version != CUSTOM_THEME_ACCENT_SCHEMA_VERSION) {
                    return CustomAccentSchemaPayload(
                        state = AccentSchemaState.UNKNOWN_VERSION,
                        seed = null,
                    )
                }

                if (token.equals("unset", ignoreCase = true)) {
                    return CustomAccentSchemaPayload(
                        state = AccentSchemaState.VALID,
                        seed = null,
                    )
                }

                val parsed = token.toUIntOrNull(16)?.toInt()
                    ?: return CustomAccentSchemaPayload(
                        state = AccentSchemaState.MALFORMED,
                        seed = null,
                    )
                val normalized = normalizeAccentSeed(parsed)
                return CustomAccentSchemaPayload(
                    state = AccentSchemaState.VALID,
                    seed = normalized.takeUnless { it == CUSTOM_THEME_ACCENT_SEED_UNSET },
                )
            }

            fun forSeed(seed: Int): CustomAccentSchemaPayload {
                val normalized = normalizeAccentSeed(seed)
                return CustomAccentSchemaPayload(
                    state = AccentSchemaState.VALID,
                    seed = normalized.takeUnless { it == CUSTOM_THEME_ACCENT_SEED_UNSET },
                )
            }
        }
    }

    private inner class CustomThemeAccentSeedPreference : Preference<Int> {
        private val legacyPreference = preferenceStore.getInt(
            PREF_CUSTOM_THEME_ACCENT_SEED,
            CUSTOM_THEME_ACCENT_SEED_UNSET,
        )
        private val schemaPreference = preferenceStore.getObject(
            key = PREF_CUSTOM_THEME_ACCENT_SCHEMA,
            defaultValue = CustomAccentSchemaPayload(
                state = AccentSchemaState.MALFORMED,
                seed = null,
            ),
            serializer = { payload ->
                val version = if (payload.state == AccentSchemaState.VALID) {
                    CUSTOM_THEME_ACCENT_SCHEMA_VERSION
                } else {
                    CUSTOM_THEME_ACCENT_SCHEMA_VERSION
                }
                val seedToken = payload.seed
                    ?.let { normalized ->
                        normalizeAccentSeed(normalized).toUInt().toString(16).padStart(8, '0')
                    }
                    ?: "unset"
                "v$version:$seedToken"
            },
            deserializer = { encoded ->
                CustomAccentSchemaPayload.fromPersisted(encoded)
            },
        )
        private val derivedChanges = merge(
            legacyPreference.changes().map { Unit },
            schemaPreference.changes().map { Unit },
        )
            .map { resolveCurrentSeed(migrate = false) }
            .distinctUntilChanged()

        override fun key(): String = PREF_CUSTOM_THEME_ACCENT_SEED

        override fun get(): Int {
            return resolveCurrentSeed(migrate = true)
        }

        override fun set(value: Int) {
            val normalized = normalizeAccentSeed(value)
            legacyPreference.set(normalized)
            schemaPreference.set(CustomAccentSchemaPayload.forSeed(normalized))
        }

        override fun isSet(): Boolean = legacyPreference.isSet() || schemaPreference.isSet()

        override fun delete() {
            legacyPreference.delete()
            schemaPreference.delete()
        }

        override fun defaultValue(): Int = CUSTOM_THEME_ACCENT_SEED_UNSET

        override fun changes(): Flow<Int> = derivedChanges

        override fun stateIn(scope: CoroutineScope): StateFlow<Int> {
            return changes().stateIn(
                scope = scope,
                started = SharingStarted.Eagerly,
                initialValue = get(),
            )
        }

        private fun resolveCurrentSeed(migrate: Boolean): Int {
            val legacySeed = normalizeAccentSeed(legacyPreference.get())
            val schemaSet = schemaPreference.isSet()
            val schemaPayload = schemaPreference.get()
            return when (schemaPayload.state) {
                AccentSchemaState.VALID -> {
                    val resolved = schemaPayload.seed ?: CUSTOM_THEME_ACCENT_SEED_UNSET
                    if (migrate && legacySeed != resolved) {
                        legacyPreference.set(resolved)
                    }
                    resolved
                }
                AccentSchemaState.UNKNOWN_VERSION -> legacySeed
                AccentSchemaState.MALFORMED -> {
                    if (migrate && schemaSet) {
                        schemaPreference.set(CustomAccentSchemaPayload.forSeed(legacySeed))
                    } else if (migrate && legacySeed != CUSTOM_THEME_ACCENT_SEED_UNSET) {
                        schemaPreference.set(CustomAccentSchemaPayload.forSeed(legacySeed))
                    }
                    legacySeed
                }
            }
        }
    }
}
