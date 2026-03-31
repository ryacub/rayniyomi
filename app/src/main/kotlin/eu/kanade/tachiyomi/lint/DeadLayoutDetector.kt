package eu.kanade.tachiyomi.lint

import java.io.File

data class DeadLayout(val name: String, val file: File)

class DeadLayoutDetector(
    private val layoutDir: File,
    private val sourceRoots: List<File>,
    private val exclusions: Set<String> = emptySet(),
) {
    fun findDeadLayouts(): List<DeadLayout> {
        if (!layoutDir.exists()) {
            return emptyList()
        }

        val xmlFiles = layoutDir.listFiles()?.filter { it.name.endsWith(".xml") } ?: return emptyList()

        val deadLayouts = mutableListOf<DeadLayout>()

        for (xmlFile in xmlFiles) {
            val layoutName = xmlFile.nameWithoutExtension

            // Skip if in exclusions
            if (layoutName in exclusions) {
                continue
            }

            // Check if referenced in any source root
            val isReferenced = isLayoutReferenced(layoutName)

            if (!isReferenced) {
                deadLayouts.add(DeadLayout(layoutName, xmlFile))
            }
        }

        return deadLayouts
    }

    private fun isLayoutReferenced(layoutName: String): Boolean {
        val patterns = buildSearchPatterns(layoutName)

        for (sourceRoot in sourceRoots) {
            if (!sourceRoot.exists()) {
                continue
            }

            // Check all .kt and .java files
            sourceRoot.walkTopDown().forEach { file ->
                if ((file.name.endsWith(".kt") || file.name.endsWith(".java")) && file.isFile) {
                    val content = file.readText()
                    for (pattern in patterns) {
                        if (content.contains(pattern)) {
                            return true
                        }
                    }
                }
            }

            // Check all .xml files
            sourceRoot.walkTopDown().forEach { file ->
                if (file.name.endsWith(".xml") && file.isFile) {
                    val content = file.readText()
                    for (pattern in patterns) {
                        if (content.contains(pattern)) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    private fun buildSearchPatterns(layoutName: String): List<String> {
        return listOf(
            // Pattern 1: R.layout.<name>
            "R.layout.$layoutName",

            // Pattern 2: @layout/<name>
            "@layout/$layoutName",

            // Pattern 3 & 4: ViewBinding patterns (PascalCase + Binding.inflate/bind)
            toPascalCaseBinding(layoutName) + ".inflate",
            toPascalCaseBinding(layoutName) + ".bind",
        )
    }

    private fun toPascalCaseBinding(layoutName: String): String {
        // foo_bar -> FooBar -> FooBarBinding
        val parts = layoutName.split("_")
        val pascalCase = parts.joinToString("") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return pascalCase + "Binding"
    }
}
