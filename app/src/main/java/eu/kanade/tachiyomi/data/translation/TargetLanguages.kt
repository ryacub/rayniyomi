package eu.kanade.tachiyomi.data.translation

import java.util.Locale

/** Catalog of selectable translation target languages, stored as BCP-47 tags. */
object TargetLanguages {
    const val DEFAULT = "en"

    /** Fixed offering; includes English and Italian per R888. */
    val supported: List<String> = listOf(
        "en", // English
        "it", // Italian
        "es",
        "fr",
        "de",
        "pt-BR",
        "ru",
        "ja",
        "ko",
        "zh-CN",
    )

    fun displayName(code: String): String =
        Locale.forLanguageTag(code).displayName.replaceFirstChar { it.uppercase() }

    /** Ordered [code -> readable name]; preserves an unsupported stored value as an extra entry. */
    fun entries(currentCode: String): Map<String, String> {
        val base = supported.associateWith(::displayName)
        if (currentCode.isBlank() || currentCode in base) return base
        return base + (currentCode to displayName(currentCode))
    }
}
