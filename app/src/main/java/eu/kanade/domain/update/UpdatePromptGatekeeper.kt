package eu.kanade.domain.update

import java.time.Instant

class UpdatePromptGatekeeper(private val prefs: UpdatePromptPreferences) {

    fun shouldPrompt(releaseVersion: String, isPrerelease: Boolean = false): Boolean {
        // 1. Check if this version is skipped
        val skippedVersion = prefs.skipVersion().get()
        if (skippedVersion == releaseVersion) {
            return false
        }

        // 2. Check if this is a prerelease and user doesn't want prerelease notifications
        if (isPrerelease && !prefs.includePrerelease().get()) {
            return false
        }

        // 3. Check cadence
        val cadence = prefs.promptCadence().get()
        return when (cadence) {
            PromptCadence.NEVER -> false
            PromptCadence.ALWAYS -> true
            PromptCadence.DAILY -> {
                val lastPromptedMs = prefs.lastPromptedAt().get()
                if (lastPromptedMs == 0L) {
                    // Never prompted before
                    true
                } else {
                    // Check if >= 24 hours have elapsed
                    val now = Instant.now()
                    val lastPrompted = Instant.ofEpochMilli(lastPromptedMs)
                    val elapsedSeconds = now.epochSecond - lastPrompted.epochSecond
                    val twentyFourHoursInSeconds = 24 * 60 * 60
                    elapsedSeconds >= twentyFourHoursInSeconds
                }
            }
            PromptCadence.WEEKLY -> {
                val lastPromptedMs = prefs.lastPromptedAt().get()
                if (lastPromptedMs == 0L) {
                    // Never prompted before
                    true
                } else {
                    // Check if >= 7 days have elapsed
                    val now = Instant.now()
                    val lastPrompted = Instant.ofEpochMilli(lastPromptedMs)
                    val elapsedSeconds = now.epochSecond - lastPrompted.epochSecond
                    val sevenDaysInSeconds = 7 * 24 * 60 * 60
                    elapsedSeconds >= sevenDaysInSeconds
                }
            }
        }
    }

    fun recordPrompted() {
        val currentTimeMs = System.currentTimeMillis()
        prefs.lastPromptedAt().set(currentTimeMs)
    }

    fun skipVersion(version: String) {
        prefs.skipVersion().set(version)
    }

    fun clearSkipIfOutdated(currentLatestVersion: String) {
        val skippedVersion = prefs.skipVersion().get()
        if (skippedVersion.isEmpty()) {
            return
        }

        // Compare versions: if currentLatestVersion is newer, clear skip
        if (isNewerVersion(currentLatestVersion, skippedVersion)) {
            prefs.skipVersion().set("")
        }
    }

    /**
     * Compare semantic versions.
     * Returns true if newVersion > oldVersion.
     * Strips non-digit/dot characters (like "v" or "r" prefix).
     */
    private fun isNewerVersion(newVersion: String, oldVersion: String): Boolean {
        val cleanedNew = newVersion.replace("[^\\d.]".toRegex(), "")
        val cleanedOld = oldVersion.replace("[^\\d.]".toRegex(), "")

        val newSemVer = cleanedNew.split(".").mapNotNull { it.toIntOrNull() }
        val oldSemVer = cleanedOld.split(".").mapNotNull { it.toIntOrNull() }

        // Compare each component in order
        for (i in 0 until maxOf(newSemVer.size, oldSemVer.size)) {
            val newComponent = newSemVer.getOrNull(i) ?: 0
            val oldComponent = oldSemVer.getOrNull(i) ?: 0

            if (newComponent > oldComponent) {
                return true
            } else if (newComponent < oldComponent) {
                return false
            }
            // If equal, continue to next component
        }

        // All components are equal
        return false
    }
}
