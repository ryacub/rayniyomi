package mihon.core.migration.migrations

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext
import java.util.Locale
import java.util.MissingResourceException
import kotlin.text.split

class PrefLangMigration : Migration {
    override val version = 130f

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.graph?.application ?: return false
        val audioPreferences = migrationContext.graph.audioPreferences
        val subtitlePreferences = migrationContext.graph.subtitlePreferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        listOf(
            audioPreferences.preferredAudioLanguages(),
            subtitlePreferences.preferredSubLanguages(),
        ).forEach { pref ->
            if (pref.isSet()) {
                prefs.edit {
                    val langs = prefs.getString(
                        pref.key(),
                        "",
                    )!!.split(",").filter(String::isNotEmpty).map(String::trim)
                    val newLangs = langs.filter { it.isValidCode() }.joinToString(",")
                    putString(pref.key(), newLangs)
                }
            }
        }

        return true
    }

    private fun String.isValidCode(): Boolean {
        try {
            val locale = Locale(this)
            if (locale.isO3Language == locale.language && locale.language == locale.getDisplayName(Locale.ENGLISH)) {
                return false
            }
        } catch (_: MissingResourceException) {
            return false
        }

        return true
    }
}
