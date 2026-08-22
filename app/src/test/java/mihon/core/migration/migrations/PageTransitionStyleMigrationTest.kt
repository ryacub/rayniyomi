package mihon.core.migration.migrations

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle
import eu.kanade.tachiyomi.ui.updates.InMemoryPreferenceStore
import kotlinx.coroutines.runBlocking
import mihon.core.migration.MigrationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.addFactory

class PageTransitionStyleMigrationTest {

    private companion object {
        const val OLD_KEY = "pref_enable_transitions_key"
        const val NEW_KEY = "pref_page_transition_style"

        val store = InMemoryPreferenceStore()
    }

    @BeforeEach
    fun setup() {
        // Injekt displays two registration modes: addSingleton caches the FIRST
        // instance for the type (getOrPut on the registrar) and later registrations
        // silently keep returning that first value. addFactory overwrites the map
        // slot and re-invokes each resolution, so the most recent registration is
        // the one that resolves. Registering per test keeps the migration reading
        // this class's store even when another migration test in the same JVM
        // registered a PreferenceStore before us.
        Injekt.addFactory<PreferenceStore> { store }
        assertSame(store, Injekt.getInstanceOrNull(PreferenceStore::class.java))
        store.getBoolean(OLD_KEY).delete()
        store.getEnum(NEW_KEY, PageTransitionStyle.SLIDE).delete()
    }

    @Test
    fun `old key true maps to SLIDE and writes the new key`() = runBlocking {
        store.getBoolean(OLD_KEY).set(true)

        assertTrue(PageTransitionStyleMigration()(MigrationContext(dryrun = false)))

        val pref = store.getEnum(NEW_KEY, PageTransitionStyle.SLIDE)
        assertTrue(pref.isSet())
        assertEquals(PageTransitionStyle.SLIDE, pref.get())
    }

    @Test
    fun `old key false maps to NONE and writes the new key`() = runBlocking {
        store.getBoolean(OLD_KEY).set(false)

        assertTrue(PageTransitionStyleMigration()(MigrationContext(dryrun = false)))

        val pref = store.getEnum(NEW_KEY, PageTransitionStyle.SLIDE)
        assertTrue(pref.isSet())
        assertEquals(PageTransitionStyle.NONE, pref.get())
    }

    @Test
    fun `old key never set maps to SLIDE and writes the new key`() = runBlocking {
        assertTrue(PageTransitionStyleMigration()(MigrationContext(dryrun = false)))

        val pref = store.getEnum(NEW_KEY, PageTransitionStyle.SLIDE)
        assertTrue(pref.isSet())
        assertEquals(PageTransitionStyle.SLIDE, pref.get())
    }

    @Test
    fun `old key survives the migration`() = runBlocking {
        store.getBoolean(OLD_KEY).set(true)

        assertTrue(PageTransitionStyleMigration()(MigrationContext(dryrun = false)))

        assertTrue(store.getBoolean(OLD_KEY).isSet())
    }

    @Test
    fun `migration reports success`() = runBlocking {
        assertTrue(PageTransitionStyleMigration()(MigrationContext(dryrun = false)))
    }
}
