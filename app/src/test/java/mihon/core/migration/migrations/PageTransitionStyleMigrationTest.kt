package mihon.core.migration.migrations

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences
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
        // addFactory, not addSingleton: addSingleton caches the first instance
        // registered for a type, so whichever migration test ran first in this JVM
        // would win and the other would silently read the wrong store.
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

    // The migration and ReaderPreferences name the new key in separate string
    // literals. Without this, a typo in either one leaves both sides passing
    // their own tests while the app reads a key the migration never wrote.
    @Test
    fun `migrated value is readable through ReaderPreferences`() = runBlocking {
        store.getBoolean(OLD_KEY).set(false)

        assertTrue(PageTransitionStyleMigration()(MigrationContext(dryrun = false)))

        assertEquals(
            PageTransitionStyle.NONE,
            ReaderPreferences(store).pageTransitionStyle().get(),
        )
    }
}
