package eu.kanade.presentation.reader

import java.util.Locale

fun formatWebtoonAutoScrollSpeed(speedTenths: Int): String {
    return String.format(Locale.US, "%.1fx", speedTenths / 10f)
}
