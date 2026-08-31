package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

@Serializable
data class ALFuzzyDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
) {
    fun toEpochMilli(): Long = try {
        LocalDate.of(year ?: return 0L, month ?: return 0L, day ?: return 0L)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    } catch (_: Exception) {
        0L
    }
}
