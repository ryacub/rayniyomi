package eu.kanade.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import eu.kanade.domain.ui.UiPreferences
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.util.lang.toRelativeString
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun relativeDateText(
    dateEpochMillis: Long,
): String {
    return relativeDateText(
        localDate = LocalDate.ofInstant(
            Instant.ofEpochMilli(dateEpochMillis),
            ZoneId.systemDefault(),
        )
            .takeIf { dateEpochMillis > 0L },
    )
}

// For use in chapter/episode release time
@Composable
fun relativeDateTimeText(
    dateEpochMillis: Long,
): String {
    return relativeDateTimeText(
        localDateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(dateEpochMillis),
            ZoneId.systemDefault(),
        )
            .takeIf { dateEpochMillis > 0L },
    )
}

@Composable
fun relativeDateText(
    localDate: LocalDate?,
): String {
    val context = LocalContext.current
    val display = rememberDateDisplay()

    return localDate?.toRelativeString(
        context = context,
        relative = display.relative,
        dateFormat = display.format,
    )
        ?: stringResource(MR.strings.not_applicable)
}

// For use in chapter/episode release time
@Composable
fun relativeDateTimeText(
    localDateTime: LocalDateTime?,
): String {
    val context = LocalContext.current
    val display = rememberDateDisplay()

    return localDateTime?.toRelativeString(
        context = context,
        relative = display.relative,
        dateFormat = display.format,
    )
        ?: stringResource(MR.strings.not_applicable)
}

private data class DateDisplay(val relative: Boolean, val format: DateTimeFormatter)

@Composable
private fun rememberDateDisplay(): DateDisplay {
    val inspection = LocalInspectionMode.current
    return remember(inspection) {
        if (inspection) {
            DateDisplay(relative = false, format = UiPreferences.dateFormat(""))
        } else {
            val preferences = appGraph.uiPreferences
            DateDisplay(
                relative = preferences.relativeTime().get(),
                format = UiPreferences.dateFormat(preferences.dateFormat().get()),
            )
        }
    }
}
