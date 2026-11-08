package eu.kanade.tachiyomi.util.episode

private val episodeCodePattern = Regex("""\bS\d+\s*-?\s*E\d+\b""", RegexOption.IGNORE_CASE)

fun episodeCode(name: String): String? = episodeCodePattern.find(name)?.value
