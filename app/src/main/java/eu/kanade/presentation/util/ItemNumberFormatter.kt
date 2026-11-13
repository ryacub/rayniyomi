package eu.kanade.presentation.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

private val formatter = DecimalFormat(
    "#.###",
    DecimalFormatSymbols().apply { decimalSeparator = '.' },
)

fun formatChapterNumber(chapterNumber: Double): String {
    return formatter.format(chapterNumber)
}

fun formatEpisodeNumber(episodeNumber: Double): String {
    return formatter.format(episodeNumber)
}

fun String.padItemNumber(): String {
    val digits = map { it.digitToIntOrNull()?.digitToChar() ?: it }.joinToString("")
    return if (digits.substringBefore('.').length == 1) "0$digits" else digits
}
