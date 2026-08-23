package eu.kanade.tachiyomi.data.translation

import eu.kanade.tachiyomi.util.system.LocaleHelper
import java.util.Locale

/** Selectable translation target languages as BCP-47 tags. */
object TargetLanguages {
    const val DEFAULT = "en"

    val supported: List<String> = listOf(
        "en",
        "it",
        "es",
        "fr",
        "de",
        "pt-BR",
        "ru",
        "ja",
        "ko",
        "zh-CN",
    )

    /** Name for the picker, in the reader's own language. */
    fun displayName(code: String): String =
        LocaleHelper.getDisplayName(code).replaceFirstChar { it.uppercase() }

    /**
     * Name for the translation prompt. Always English, and always a full name, because
     * models translate to "Brazilian Portuguese" more reliably than to "pt-BR".
     */
    fun promptName(code: String): String =
        Locale.forLanguageTag(LocaleHelper.normalize(code))
            .getDisplayName(Locale.ENGLISH)
            .ifBlank { code }

    /** Appends [currentCode] as an extra entry when it is not in [supported]. */
    fun entries(currentCode: String): Map<String, String> {
        val base = supported.associateWith(::displayName)
        if (currentCode.isBlank() || currentCode in base) return base
        return base + (currentCode to displayName(currentCode))
    }
}
