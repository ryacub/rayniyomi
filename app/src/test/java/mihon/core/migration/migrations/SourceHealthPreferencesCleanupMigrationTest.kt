package mihon.core.migration.migrations

import eu.kanade.tachiyomi.di.testAppGraph
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import mihon.core.migration.MigrationContext
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.core.common.preference.PreferenceStore

class SourceHealthPreferencesCleanupMigrationTest {

    @Test
    fun `deletes both orphaned show_broken keys and touches nothing else`() = runBlocking {
        val mangaPreference = mockk<Preference<Boolean>>(relaxed = true)
        val animePreference = mockk<Preference<Boolean>>(relaxed = true)
        val unrelatedPreference = mockk<Preference<Boolean>>(relaxed = true)
        val preferenceStore = mockk<PreferenceStore> {
            every { getBoolean(any(), any()) } returns unrelatedPreference
            every { getBoolean("show_broken_manga_sources", any()) } returns mangaPreference
            every { getBoolean("show_broken_anime_sources", any()) } returns animePreference
        }
        every { testAppGraph.preferenceStore } returns preferenceStore

        assertTrue(SourceHealthPreferencesCleanupMigration()(MigrationContext(dryrun = false)))

        verify(exactly = 1) { mangaPreference.delete() }
        verify(exactly = 1) { animePreference.delete() }
        verify(exactly = 0) { unrelatedPreference.delete() }
    }
}
