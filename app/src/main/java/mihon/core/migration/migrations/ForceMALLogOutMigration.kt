package mihon.core.migration.migrations

import mihon.core.migration.Migration
import mihon.core.migration.MigrationContext

class ForceMALLogOutMigration : Migration {
    override val version = 54f

    // Force MAL log out due to login flow change
    // v52: switched from scraping to WebView
    // v53: switched from WebView to OAuth
    override suspend fun invoke(migrationContext: MigrationContext): Boolean {
        val trackerManager = migrationContext.graph?.trackerManager ?: return false

        if (trackerManager.myAnimeList.isLoggedIn) {
            trackerManager.myAnimeList.logout()
        }

        return true
    }
}
