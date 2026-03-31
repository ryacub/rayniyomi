package eu.kanade.tachiyomi.lint

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Tests for DeadLayoutDetector business logic.
 *
 * Tests cover detection of unreferenced XML layouts via multiple reference patterns:
 * - R.layout.<name> in .kt/.java files
 * - @layout/<name> in .xml includes
 * - <Name>Binding.inflate / <Name>Binding.bind in .kt/.java files (ViewBinding)
 *
 * Also tests exclusion sets, non-existent directories, and edge cases.
 */
class DeadLayoutDetectorTest {

    @TempDir
    lateinit var tempDir: Path

    // ============================================================================
    // EMPTY LAYOUT DIR
    // ============================================================================

    @Test
    fun `findDeadLayouts returns empty list when layout dir has no layouts`() {
        // Arrange
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        val sourceRoots = listOf(tempDir.resolve("src/main/kotlin").toFile().apply { mkdirs() })
        val detector = DeadLayoutDetector(layoutDir, sourceRoots)

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(emptyList<DeadLayout>(), result)
    }

    // ============================================================================
    // R.layout.<name> PATTERN
    // ============================================================================

    @Test
    fun `findDeadLayouts returns empty list when layout is referenced via R_layout_name`() {
        // Arrange
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        layoutDir.resolve("foo_bar.xml").writeText("<layout/>")
        val sourceRoot = tempDir.resolve("src/main/kotlin").toFile().apply { mkdirs() }
        sourceRoot.resolve("Activity.kt").writeText("""
            import android.os.Bundle
            class MyActivity {
                fun onCreate(savedInstanceState: Bundle?) {
                    setContentView(R.layout.foo_bar)
                }
            }
        """.trimIndent())
        val detector = DeadLayoutDetector(layoutDir, listOf(sourceRoot))

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(emptyList<DeadLayout>(), result)
    }

    // ============================================================================
    // @layout/<name> PATTERN (XML includes)
    // ============================================================================

    @Test
    fun `findDeadLayouts returns empty list when layout is referenced via @layout tag in XML`() {
        // Arrange
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        layoutDir.resolve("foo_layout.xml").writeText("<layout/>")
        val sourceRoot = tempDir.resolve("src/main/res").toFile().apply { mkdirs() }
        sourceRoot.resolve("layout_include.xml").writeText("""
            <merge>
                <include layout="@layout/foo_layout"/>
            </merge>
        """.trimIndent())
        val detector = DeadLayoutDetector(layoutDir, listOf(sourceRoot))

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(emptyList<DeadLayout>(), result)
    }

    // ============================================================================
    // VIEWBINDING PATTERNS (inflate and bind)
    // ============================================================================

    @Test
    fun `findDeadLayouts returns empty list when layout is referenced via FooBinding_inflate`() {
        // Arrange
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        layoutDir.resolve("player_layout.xml").writeText("<layout/>")
        val sourceRoot = tempDir.resolve("src/main/kotlin").toFile().apply { mkdirs() }
        sourceRoot.resolve("Activity.kt").writeText("""
            import com.example.databinding.PlayerLayoutBinding
            class PlayerActivity {
                fun onCreate() {
                    val binding = PlayerLayoutBinding.inflate(layoutInflater)
                }
            }
        """.trimIndent())
        val detector = DeadLayoutDetector(layoutDir, listOf(sourceRoot))

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(emptyList<DeadLayout>(), result)
    }

    @Test
    fun `findDeadLayouts returns empty list when layout is referenced via FooBinding_bind`() {
        // Arrange
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        layoutDir.resolve("reader_activity.xml").writeText("<layout/>")
        val sourceRoot = tempDir.resolve("src/main/kotlin").toFile().apply { mkdirs() }
        sourceRoot.resolve("Activity.kt").writeText("""
            import com.example.databinding.ReaderActivityBinding
            class ReaderActivity {
                fun setupView(root: View) {
                    val binding = ReaderActivityBinding.bind(root)
                }
            }
        """.trimIndent())
        val detector = DeadLayoutDetector(layoutDir, listOf(sourceRoot))

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(emptyList<DeadLayout>(), result)
    }

    // ============================================================================
    // DEAD LAYOUT (zero references)
    // ============================================================================

    @Test
    fun `findDeadLayouts returns layout with zero references in dead list`() {
        // Arrange
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        val deadLayout = layoutDir.resolve("unused_layout.xml").apply { writeText("<layout/>") }
        val sourceRoot = tempDir.resolve("src/main/kotlin").toFile().apply { mkdirs() }
        sourceRoot.resolve("Activity.kt").writeText("""
            class MyActivity {
                fun onCreate() {
                    // No reference to unused_layout
                }
            }
        """.trimIndent())
        val detector = DeadLayoutDetector(layoutDir, listOf(sourceRoot))

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(1, result.size)
        assertEquals("unused_layout", result[0].name)
        assertEquals(deadLayout, result[0].file)
    }

    // ============================================================================
    // EXCLUSION SET
    // ============================================================================

    @Test
    fun `findDeadLayouts excludes layouts in exclusions set even when unreferenced`() {
        // Arrange
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        layoutDir.resolve("migrated_layout.xml").writeText("<layout/>")
        val sourceRoot = tempDir.resolve("src/main/kotlin").toFile().apply { mkdirs() }
        sourceRoot.resolve("Activity.kt").writeText("class MyActivity {}")
        val exclusions = setOf("migrated_layout")
        val detector = DeadLayoutDetector(layoutDir, listOf(sourceRoot), exclusions)

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(emptyList<DeadLayout>(), result)
    }

    // ============================================================================
    // NON-EXISTENT LAYOUT DIR
    // ============================================================================

    @Test
    fun `findDeadLayouts returns empty list when layout dir does not exist`() {
        // Arrange
        val layoutDir = tempDir.resolve("nonexistent/layout").toFile()
        val sourceRoot = tempDir.resolve("src/main/kotlin").toFile().apply { mkdirs() }
        val detector = DeadLayoutDetector(layoutDir, listOf(sourceRoot))

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(emptyList<DeadLayout>(), result)
    }

    // ============================================================================
    // MULTIPLE LAYOUTS
    // ============================================================================

    @Test
    fun `findDeadLayouts returns only unreferenced layouts from multiple`() {
        // Arrange
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        val referenced = layoutDir.resolve("used_layout.xml").apply { writeText("<layout/>") }
        val dead1 = layoutDir.resolve("unused_1.xml").apply { writeText("<layout/>") }
        val dead2 = layoutDir.resolve("unused_2.xml").apply { writeText("<layout/>") }
        val sourceRoot = tempDir.resolve("src/main/kotlin").toFile().apply { mkdirs() }
        sourceRoot.resolve("Activity.kt").writeText("""
            class MyActivity {
                fun onCreate() {
                    setContentView(R.layout.used_layout)
                }
            }
        """.trimIndent())
        val detector = DeadLayoutDetector(layoutDir, listOf(sourceRoot))

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(2, result.size)
        assertTrue(result.any { it.name == "unused_1" })
        assertTrue(result.any { it.name == "unused_2" })
        assertTrue(result.all { it.name != "used_layout" })
    }

    // ============================================================================
    // MISSING SOURCE ROOT
    // ============================================================================

    @Test
    fun `findDeadLayouts handles missing source root gracefully`() {
        // Arrange
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        val dead = layoutDir.resolve("unused_layout.xml").apply { writeText("<layout/>") }
        // Provide a source root that doesn't exist
        val nonexistentSourceRoot = tempDir.resolve("nonexistent/src").toFile()
        val detector = DeadLayoutDetector(layoutDir, listOf(nonexistentSourceRoot))

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(1, result.size)
        assertEquals("unused_layout", result[0].name)
    }

    // ============================================================================
    // MULTIPLE SOURCE ROOTS
    // ============================================================================

    @Test
    fun `findDeadLayouts scans all source roots for references`() {
        // Arrange
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        val dead = layoutDir.resolve("unused.xml").apply { writeText("<layout/>") }
        val referenced = layoutDir.resolve("used.xml").apply { writeText("<layout/>") }

        val sourceRoot1 = tempDir.resolve("src/main/kotlin").toFile().apply { mkdirs() }
        sourceRoot1.resolve("Activity.kt").writeText("class A {}")

        val sourceRoot2 = tempDir.resolve("src/main/java").toFile().apply { mkdirs() }
        sourceRoot2.resolve("Activity.java").writeText("""
            class B {
                void onCreate() {
                    setContentView(R.layout.used);
                }
            }
        """.trimIndent())

        val detector = DeadLayoutDetector(layoutDir, listOf(sourceRoot1, sourceRoot2))

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(1, result.size)
        assertEquals("unused", result[0].name)
    }

    // ============================================================================
    // REAL PROJECT FIXTURES (6 dead layout files)
    // ============================================================================

    @Test
    fun `project contains 6 dead layout fixture files for testing`() {
        // Pre-condition: assert the 6 target files exist in the actual project structure
        val projectLayoutDir = java.io.File("src/main/res/layout")

        assertTrue(projectLayoutDir.exists(), "Layout dir should exist: $projectLayoutDir")

        val requiredFiles = listOf(
            "player_layout.xml",
            "reader_activity.xml",
            "reader_error.xml",
            "download_header.xml",
            "download_item.xml",
            "download_list.xml",
        )

        for (filename in requiredFiles) {
            val file = projectLayoutDir.resolve(filename)
            assertTrue(file.exists(), "Required fixture file should exist: $filename")
        }
    }

    // ============================================================================
    // CASE SENSITIVITY
    // ============================================================================

    @Test
    fun `findDeadLayouts detects underscore-to-camelcase binding pattern conversion`() {
        // Arrange: layout foo_bar.xml should match FooBarBinding
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        layoutDir.resolve("foo_bar.xml").writeText("<layout/>")
        val sourceRoot = tempDir.resolve("src/main/kotlin").toFile().apply { mkdirs() }
        sourceRoot.resolve("Activity.kt").writeText("""
            class MyActivity {
                fun onCreate() {
                    val binding = FooBarBinding.inflate(layoutInflater)
                }
            }
        """.trimIndent())
        val detector = DeadLayoutDetector(layoutDir, listOf(sourceRoot))

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(emptyList<DeadLayout>(), result)
    }

    // ============================================================================
    // REFERENCE IN XML (include with underscore_name)
    // ============================================================================

    @Test
    fun `findDeadLayouts finds references in XML files using @layout_underscore_name`() {
        // Arrange
        val layoutDir = tempDir.resolve("layout").toFile().apply { mkdirs() }
        layoutDir.resolve("my_layout.xml").writeText("<layout/>")
        val sourceRoot = tempDir.resolve("src/main/res").toFile().apply { mkdirs() }
        sourceRoot.resolve("container.xml").writeText("""
            <merge>
                <include layout="@layout/my_layout"/>
            </merge>
        """.trimIndent())
        val detector = DeadLayoutDetector(layoutDir, listOf(sourceRoot))

        // Act
        val result = detector.findDeadLayouts()

        // Assert
        assertEquals(emptyList<DeadLayout>(), result)
    }
}
