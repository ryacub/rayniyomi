package eu.kanade.tachiyomi.ui.library

import tachiyomi.core.common.preference.TriState
import tachiyomi.domain.entries.applyFilter

internal fun matchesCustomIntervalFilter(filter: TriState, fetchInterval: Int): Boolean {
    return applyFilter(filter) { fetchInterval < 0 }
}
