package tachiyomi.domain.recovery

import tachiyomi.domain.recovery.model.NewRecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryState

object RecoverySessionEntries {

    data class Original(val id: Long, val title: String, val seasons: Boolean)

    fun from(originals: List<Original>): List<NewRecoveryEntry> = originals.map {
        NewRecoveryEntry(
            originalId = it.id,
            searchQuery = it.title,
            state = if (it.seasons) RecoveryState.UNSUPPORTED else RecoveryState.SELECTED,
        )
    }
}
