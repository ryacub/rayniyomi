package mihon.buildlogic.tasks

import java.io.File
import org.jetbrains.kotlin.CoreEnvironmentDeprecation
import org.jetbrains.kotlin.cli.extensionsStorage
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

object CheckAppGraphLookups {

    @OptIn(CoreEnvironmentDeprecation::class, CompilerConfiguration.Internals::class, ExperimentalCompilerApi::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val printBaseline = args.singleOrNull() == "--print-baseline"
        val projectDir = File(".").absoluteFile.normalize()
        val baseline = projectDir.resolve("scripts/app_graph_lookup_baseline.txt").readLines()
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .toMutableMap()
        val disposable = Disposer.newDisposable()
        var count = 0
        var newCount = 0
        try {
            val environment = KotlinCoreEnvironment.createForProduction(
                disposable,
                CompilerConfiguration().apply { extensionsStorage = CompilerPluginRegistrar.ExtensionStorage() },
                EnvironmentConfigFiles.JVM_CONFIG_FILES,
            )
            val factory = KtPsiFactory(environment.project)
            val sourceSets = projectDir.resolve("app/src").listFiles().orEmpty()
                .filter { it.isDirectory && !it.name.startsWith("test") && !it.name.startsWith("androidTest") }
            val sourceFiles = sourceSets.asSequence()
                .flatMap { it.walkTopDown() }
                .filter { it.isFile && it.extension == "kt" }
                .sortedBy { it.path }
            for (file in sourceFiles) {
                val source = file.readText()
                if ("appGraph" !in source) continue
                val path = file.relativeTo(projectDir).invariantSeparatorsPath
                val syntax = factory.createFile(file.name, source)
                syntax.accept(object : KtTreeVisitorVoid() {
                    override fun visitQualifiedExpression(expression: KtQualifiedExpression) {
                        super.visitQualifiedExpression(expression)
                        val receiver = expression.receiverExpression as? KtNameReferenceExpression ?: return
                        if (receiver.getReferencedName() != "appGraph") return
                        val offset = receiver.textOffset
                        val declaration = generateSequence(receiver.parent) { it.parent }
                            .firstOrNull { it is KtNamedFunction || it is KtParameter }
                        val body = (declaration as? KtNamedFunction)?.bodyExpression ?: return
                        if (!body.textRange.containsOffset(offset)) return

                        count++
                        val line = syntax.text.substring(
                            syntax.text.lastIndexOf('\n', offset - 1) + 1,
                            syntax.text.indexOf('\n', offset).let { if (it < 0) syntax.textLength else it },
                        ).trim()
                        val fingerprint = "$path\t$line"
                        val remaining = baseline[fingerprint] ?: 0
                        when {
                            printBaseline -> println(fingerprint)
                            remaining > 0 -> baseline[fingerprint] = remaining - 1
                            else -> {
                                newCount++
                                val lineNumber = syntax.text.take(offset).count { it == '\n' } + 1
                                System.err.println("APP_GRAPH_LOOKUP: $path:$lineNumber: $line")
                            }
                        }
                    }
                })
            }
        } finally {
            Disposer.dispose(disposable)
        }

        if (printBaseline) return
        val staleCount = baseline.values.sum()
        baseline.filterValues { it > 0 }.forEach { (fingerprint, _) ->
            System.err.println("STALE_APP_GRAPH_BASELINE: $fingerprint")
        }
        if (newCount > 0 || staleCount > 0) {
            error(
                "Found $newCount new appGraph lookup(s) and $staleCount stale baseline entry(ies). " +
                    "See docs/app-graph-lookup-baseline.md.",
            )
        }
        println("checkAppGraphLookups: $count production function body lookups match the baseline")
    }
}
