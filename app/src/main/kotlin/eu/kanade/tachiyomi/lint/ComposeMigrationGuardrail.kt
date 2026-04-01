package eu.kanade.tachiyomi.lint

import java.io.File

class ComposeMigrationGuardrail(
    private val layoutDir: File,
    private val migratedLayouts: Set<String>,
) {
    fun findReintroductions(): List<File> {
        if (!layoutDir.exists()) return emptyList()
        return layoutDir.listFiles()
            ?.filter { it.extension == "xml" && it.nameWithoutExtension in migratedLayouts }
            ?: emptyList()
    }
}
