package mihon.core.migration.migrations

import androidx.core.content.edit
import androidx.preference.PreferenceManager
import eu.kanade.tachiyomi.di.appGraph
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class VideoPlayerPreferenceMigration : Migration {
    override val version = 126f

    private val json: Json by lazy { appGraph.json }

    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val context = migrationContext.graph?.application ?: return false
        val subtitlePreferences = migrationContext.graph.subtitlePreferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val subtitleConf = prefs.getString("pref_sub_select_conf", "")!!
        val subtitleData = try {
            json.decodeFromString<SubConfig>(subtitleConf)
        } catch (e: SerializationException) {
            return false
        }

        prefs.edit {
            putString(subtitlePreferences.preferredSubLanguages().key(), subtitleData.lang.joinToString(","))
            putString(subtitlePreferences.subtitleWhitelist().key(), subtitleData.whitelist.joinToString(","))
            putString(subtitlePreferences.subtitleBlacklist().key(), subtitleData.blacklist.joinToString(","))
        }

        return true
    }

    @Serializable
    data class SubConfig(
        val lang: List<String> = emptyList(),
        val blacklist: List<String> = emptyList(),
        val whitelist: List<String> = emptyList(),
    )
}
