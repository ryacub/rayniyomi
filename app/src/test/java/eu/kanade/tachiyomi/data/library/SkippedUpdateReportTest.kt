package eu.kanade.tachiyomi.data.library

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.nio.file.Files
import java.nio.file.Path

class SkippedUpdateReportTest {

    @ParameterizedTest
    @EnumSource(AutoUpdateSkipReason::class)
    internal fun `manual report lists reason, source, and title for every skip reason`(reason: AutoUpdateSkipReason) {
        val skipped = listOf(SkippedUpdate(reason, source = "Source A", title = "Title A"))

        val report = formatSkippedUpdateReport(
            header = "Header",
            skipped = skippedUpdatesForReport(isManualRun = true, skipped = skipped),
            reasonLabel = { "label-${it.name}" },
        )

        report shouldBe "Header\n\n\n! label-${reason.name}\n  # Source A\n    - Title A\n"
    }

    @Test
    fun `manual run with no skips has no report`() {
        skippedUpdatesForReport(isManualRun = true, skipped = emptyList()).shouldBeEmpty()
    }

    @Test
    fun `automatic run has no report even with skips`() {
        val skipped = AutoUpdateSkipReason.entries.map { SkippedUpdate(it, "Source", "Title") }

        skippedUpdatesForReport(isManualRun = false, skipped = skipped).shouldBeEmpty()
    }

    @Test
    fun `manual run keeps every skipped item`() {
        val skipped = AutoUpdateSkipReason.entries.map { SkippedUpdate(it, "Source", "Title ${it.name}") }

        skippedUpdatesForReport(isManualRun = true, skipped = skipped) shouldContainExactly skipped
    }

    @Test
    fun `report groups by reason then source with sorted titles`() {
        val skipped = listOf(
            SkippedUpdate(AutoUpdateSkipReason.OUTSIDE_RELEASE_PERIOD, "Source B", "Zeta"),
            SkippedUpdate(AutoUpdateSkipReason.COMPLETED, "Source B", "Beta"),
            SkippedUpdate(AutoUpdateSkipReason.COMPLETED, "Source A", "Gamma"),
            SkippedUpdate(AutoUpdateSkipReason.COMPLETED, "Source B", "Alpha"),
        )

        val report = formatSkippedUpdateReport("H", skipped) { it.name }

        report shouldBe "H\n\n" +
            "\n! COMPLETED\n  # Source A\n    - Gamma\n  # Source B\n    - Alpha\n    - Beta\n" +
            "\n! OUTSIDE_RELEASE_PERIOD\n  # Source B\n    - Zeta\n"
    }

    @Test
    fun `every skip reason has its own label`() {
        val labels = AutoUpdateSkipReason.entries.map { it.labelRes }

        labels.toSet().size shouldBe AutoUpdateSkipReason.entries.size
    }

    @Test
    fun `manga and anime jobs use the same manual skip report policy`() {
        listOf(
            "app/src/main/java/eu/kanade/tachiyomi/data/library/manga/MangaLibraryUpdateJob.kt",
            "app/src/main/java/eu/kanade/tachiyomi/data/library/anime/AnimeLibraryUpdateJob.kt",
        ).forEach { path ->
            val job = source(path)
            job shouldContain "isManualRun = WORK_NAME_MANUAL in tags"
            job shouldContain "skippedUpdatesForReport("
            job shouldContain "formatSkippedUpdateReport("
            job shouldContain "showUpdateSkippedNotification("
        }
    }

    private fun source(path: String): String {
        val root = generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        return Files.readString(root.resolve(path))
    }
}
