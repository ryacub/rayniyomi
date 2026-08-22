package mihon.core.migration.migrations

import eu.kanade.tachiyomi.ui.reader.setting.ReaderPreferences.PageTransitionStyle
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import tachiyomi.core.common.preference.PreferenceStore
import tachiyomi.core.common.preference.getEnum
import tachiyomi.core.common.util.lang.withIOContext

class PageTransitionStyleMigration : Migration {
    // Migration versions are app version codes, matched against (previousCode + 1)..currentCode.
    // 403 is the code this ships in: main is at 402 and the release bump lands with the merge.
    override val version: Float = 403f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean = withIOContext {
        val preferenceStore = migrationContext.get<PreferenceStore>() ?: return@withIOContext false

        val enabled = preferenceStore.getBoolean("pref_enable_transitions_key", true).get()
        val style = if (enabled) PageTransitionStyle.SLIDE else PageTransitionStyle.NONE

        preferenceStore.getEnum("pref_page_transition_style", PageTransitionStyle.SLIDE).set(style)

        return@withIOContext true
    }
}
