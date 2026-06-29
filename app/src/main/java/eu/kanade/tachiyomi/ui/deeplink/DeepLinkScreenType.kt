package eu.kanade.tachiyomi.ui.deeplink

enum class DeepLinkScreenType {
    ANIME,
    MANGA,
    ;

    companion object {
        fun fromIntentExtra(value: String?): DeepLinkScreenType {
            return value
                ?.takeUnless { it.isBlank() }
                ?.let { runCatching { valueOf(it) }.getOrNull() }
                ?: ANIME
        }
    }
}
