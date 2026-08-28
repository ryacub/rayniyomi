package eu.kanade.tachiyomi.di

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class AppModuleInjektContractTest {

    private val sourceRoot = File("src/main/java")

    @Test
    fun `every type App resolves from Injekt is registered by an imported module`() {
        assertTrue(
            sourceRoot.isDirectory,
            "Cannot find ${sourceRoot.path}; the test must run with the app module as the working directory",
        )

        val appSource = readRequiredFile(File(sourceRoot, "eu/kanade/tachiyomi/App.kt"))
        val resolvedTypes = resolvedTypesFromApp(appSource)
        assertTrue(resolvedTypes.isNotEmpty(), "App.kt resolves no Injekt types; there is nothing to check")

        val importedModules = importedModuleNames(appSource)
        assertTrue(importedModules.isNotEmpty(), "App.kt imports no Injekt modules; there is nothing to check")

        val registeredTypes = moduleSources(importedModules).values
            .flatMap { registeredTypesFromModule(it) }
            .toSet()

        val missingTypes = resolvedTypes - registeredTypes
        assertTrue(
            missingTypes.isEmpty(),
            "Types resolved in App.kt but not registered by an imported module: ${missingTypes.sorted()}. " +
                "Add a registration for each to one of the modules $importedModules.",
        )
    }

    private fun readRequiredFile(file: File): String {
        assertTrue(file.isFile, "Required source file is missing: ${file.path}")
        return file.readText()
    }

    private fun resolvedTypesFromApp(appSource: String): Set<String> {
        val resolved = mutableSetOf<String>()
        injektGetPattern.findAll(appSource).forEach { match ->
            resolved += match.groupValues[1].simpleName()
        }
        injectLazyPattern.findAll(appSource).forEach { match ->
            resolved += match.groupValues[1].simpleName()
        }
        return resolved
    }

    private fun importedModuleNames(appSource: String): List<String> =
        importModulePattern.findAll(appSource).map { it.groupValues[1] }.toList()

    private fun moduleSources(moduleNames: List<String>): Map<String, String> {
        val patterns = moduleNames.associateWith { moduleDeclarationPattern(it) }
        val found = mutableMapOf<String, String>()
        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            if (found.size == moduleNames.size) return@forEach
            val source = file.readText()
            patterns.forEach { (name, pattern) ->
                if (found[name] == null && pattern.containsMatchIn(source)) found[name] = source
            }
        }
        val missing = moduleNames.filter { found[it] == null }
        assertTrue(
            missing.isEmpty(),
            "Cannot find a source file declaring Injekt module(s) $missing under ${sourceRoot.path}",
        )
        return found
    }

    private fun registeredTypesFromModule(moduleSource: String): Set<String> {
        val registered = mutableSetOf<String>()
        registrationPattern.findAll(moduleSource).forEach { match ->
            val explicitType = match.groupValues[1].takeIf { it.isNotEmpty() }
            val type =
                explicitType ?: constructorCallPattern.find(match.groupValues[2])?.value?.substringBefore('(')?.trim()
            type?.let { registered += it.simpleName() }
        }
        typedAddSingletonPattern.findAll(moduleSource).forEach { match ->
            registered += match.groupValues[1].simpleName()
        }
        return registered
    }

    private fun String.simpleName(): String = substringAfterLast('.').substringBefore('<').trim()
}

private val injektGetPattern = Regex("Injekt\\.get<([^>]+)>")
private val injectLazyPattern = Regex(":\\s*([A-Za-z0-9_.]+)\\s+by\\s+injectLazy\\(")
private val importModulePattern = Regex("Injekt\\.importModule\\(\\s*([A-Za-z0-9_]+)\\s*\\(")
private val registrationPattern =
    Regex("add(?:SingletonFactory|Factory)\\s*(?:<([A-Za-z0-9_.]+)>)?\\s*\\{([\\s\\S]*?)\\}")
private val constructorCallPattern = Regex("[A-Za-z_][A-Za-z0-9_]*\\s*\\(")
private val typedAddSingletonPattern = Regex("addSingleton<([A-Za-z0-9_.]+)>")

private fun moduleDeclarationPattern(moduleName: String): Regex =
    Regex("(?m)^\\s*class\\s+$moduleName\\b[^{\\n]*\\bInjektModule")
