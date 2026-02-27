package mihon.buildlogic.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

private val emptyResourcesElement = "<resources>\\s*</resources>|<resources\\s*/>".toRegex()

abstract class GenerateLocalesConfigTask : DefaultTask() {

    @get:InputFiles
    lateinit var mokoResourcesTree: FileTree

    @get:OutputDirectory
    abstract val outputResourceDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val locales = mokoResourcesTree
            .matching { include("**/strings.xml") }
            .filterNot { it.readText().contains(emptyResourcesElement) }
            .map {
                it.parentFile.name
                    .replace("base", "en")
                    .replace("-r", "-")
                    .replace("+", "-")
            }
            .sorted()
            .joinToString("\n") { "|   <locale android:name=\"$it\"/>" }

        val content = """
        |<?xml version="1.0" encoding="utf-8"?>
        |<locale-config xmlns:android="http://schemas.android.com/apk/res/android">
        $locales
        |</locale-config>
        """.trimMargin()

        outputResourceDir.get().asFile.resolve("xml/locales_config.xml").apply {
            parentFile.mkdirs()
            writeText(content)
        }
    }
}
