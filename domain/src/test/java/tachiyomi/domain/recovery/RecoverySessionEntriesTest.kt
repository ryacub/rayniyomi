package tachiyomi.domain.recovery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import tachiyomi.domain.recovery.model.NewRecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryState

class RecoverySessionEntriesTest {

    @Test
    fun `a season-type original enters the session unsupported and the rest are selected`() {
        val entries = RecoverySessionEntries.from(
            listOf(
                RecoverySessionEntries.Original(id = 1, title = "One", seasons = false),
                RecoverySessionEntries.Original(id = 2, title = "Two", seasons = true),
            ),
        )

        assertEquals(
            listOf(
                NewRecoveryEntry(originalId = 1, searchQuery = "One", state = RecoveryState.SELECTED),
                NewRecoveryEntry(originalId = 2, searchQuery = "Two", state = RecoveryState.UNSUPPORTED),
            ),
            entries,
        )
    }
}
