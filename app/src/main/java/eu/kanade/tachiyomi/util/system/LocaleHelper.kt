package eu.kanade.tachiyomi.util.system

import android.content.Context
import androidx.core.os.LocaleListCompat
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.i18n.MR
import java.util.Locale

/**
 * Utility class to change the application's language in runtime.
 */
object LocaleHelper {

    /**
     * Sorts by display name, except keeps the "all" (displayed as "Multi") locale at the top.
     */
    val comparator = { a: String, b: String ->
        if (a == "all") {
            -1
        } else if (b == "all") {
            1
        } else {
            getLocalizedDisplayName(a).compareTo(getLocalizedDisplayName(b))
        }
    }

    /**
     * Returns display name of a string language code.
     */
    fun getSourceDisplayName(lang: String?, context: Context): String {
        return when (lang) {
            LAST_USED_KEY -> context.stringResource(MR.strings.last_used_source)
            PINNED_KEY -> context.stringResource(MR.strings.pinned_sources)
            "other" -> context.stringResource(MR.strings.other_source)
            "all" -> context.stringResource(MR.strings.multi_lang)
            else -> getLocalizedDisplayName(lang)
        }
    }

    /**
     * Maps legacy Chinese region tags onto script tags, so display names read
     * "Chinese (Simplified)" rather than "Chinese (China)".
     */
    fun normalize(lang: String): String = when (lang) {
        "zh-CN" -> "zh-Hans"
        "zh-TW" -> "zh-Hant"
        else -> lang
    }

    fun getDisplayName(lang: String): String {
        return Locale.forLanguageTag(normalize(lang)).displayName
    }

    /**
     * Returns display name of a string language code.
     *
     * @param lang empty for system language
     */
    fun getLocalizedDisplayName(lang: String?): String {
        if (lang == null) {
            return ""
        }

        val locale = when (lang) {
            "" -> LocaleListCompat.getAdjustedDefault()[0]
            else -> Locale.forLanguageTag(normalize(lang))
        }
        return locale!!.getDisplayName(locale).replaceFirstChar { it.uppercase(locale) }
    }

    /**
     * Return the default languages enabled for the sources.
     */
    fun getDefaultEnabledLanguages(): Set<String> {
        return setOf("all", "en", Locale.getDefault().language)
    }

    /**
     * Return English display string from string language code
     */
    fun getSimpleLocaleDisplayName(): String {
        return LocaleListCompat.getDefault()[0]!!.displayLanguage
    }
}

internal const val PINNED_KEY = "pinned"
internal const val LAST_USED_KEY = "last_used"
