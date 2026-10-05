package eu.kanade.tachiyomi.data.library

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class LibraryUpdateNotificationOwnershipTest {

    @Test
    fun `library update notifiers do not accept caller coroutine scopes`() {
        val animeNotifier = source(
            "app/src/main/java/eu/kanade/tachiyomi/data/library/anime/AnimeLibraryUpdateNotifier.kt",
        )
        val mangaNotifier = source(
            "app/src/main/java/eu/kanade/tachiyomi/data/library/manga/MangaLibraryUpdateNotifier.kt",
        )

        assertFalse(
            animeNotifier.contains("CoroutineScope"),
            "Anime notifier should not expose coroutine ownership",
        )
        assertFalse(
            mangaNotifier.contains("CoroutineScope"),
            "Manga notifier should not expose coroutine ownership",
        )
    }

    @Test
    fun `library update jobs own async detail notification dispatch`() {
        val animeJob = source(
            "app/src/main/java/eu/kanade/tachiyomi/data/library/anime/AnimeLibraryUpdateJob.kt",
        )
        val mangaJob = source(
            "app/src/main/java/eu/kanade/tachiyomi/data/library/manga/MangaLibraryUpdateJob.kt",
        )

        assertDetailDispatchOwnedByJob(animeJob, "Anime")
        assertDetailDispatchOwnedByJob(mangaJob, "Manga")
    }

    @Test
    fun `library update jobs register summaries before cancellable setup`() {
        val animeJob = source(
            "app/src/main/java/eu/kanade/tachiyomi/data/library/anime/AnimeLibraryUpdateJob.kt",
        )
        val mangaJob = source(
            "app/src/main/java/eu/kanade/tachiyomi/data/library/manga/MangaLibraryUpdateJob.kt",
        )

        assertSummaryCoversCancellableSetup(animeJob, "Anime")
        assertSummaryCoversCancellableSetup(mangaJob, "Manga")
    }

    private fun assertDetailDispatchOwnedByJob(source: String, label: String) {
        val guardIndex = source.indexOf("if (notifier.shouldShowUpdateDetailNotifications())")
        val launchIndex = source.indexOf("launch(Dispatchers.Main)", startIndex = guardIndex)
        val detailIndex = source.indexOf("notifier.showUpdateDetailNotifications", startIndex = launchIndex)
        val downloadsIndex = source.indexOf("downloadManager.startDownloads()", startIndex = detailIndex)

        assertTrue(guardIndex >= 0, "$label job should guard detail notification dispatch")
        assertTrue(launchIndex > guardIndex, "$label job should launch detail notifications from the job")
        assertTrue(detailIndex > launchIndex, "$label job should call detail notifications inside the launch")
        assertTrue(downloadsIndex > detailIndex, "$label job should preserve fire-and-forget ordering before downloads")
    }

    private fun assertSummaryCoversCancellableSetup(source: String, label: String) {
        val summaryStartIndex = source.indexOf("summaryStore.start(")
        val outerTryIndex = source.indexOf("return try {", startIndex = summaryStartIndex)
        val foregroundIndex = source.indexOf("setForeground(getForegroundInfo())", startIndex = outerTryIndex)
        val cancellationRethrowIndex = source.indexOf(
            "if (e is CancellationException) throw e",
            startIndex = foregroundIndex,
        )
        val ioContextIndex = source.indexOf("withIOContext {", startIndex = cancellationRethrowIndex)
        val outerCatchIndex = source.indexOf("} catch (e: Exception) {", startIndex = ioContextIndex)
        val nonCancellableFinishIndex = source.indexOf("withContext(NonCancellable)", startIndex = outerCatchIndex)

        assertTrue(summaryStartIndex >= 0, "$label job should register its summary execution")
        assertTrue(outerTryIndex > summaryStartIndex, "$label job should guard all cancellable setup")
        assertTrue(foregroundIndex > outerTryIndex, "$label foreground setup should be inside the guarded work")
        assertTrue(
            cancellationRethrowIndex > foregroundIndex,
            "$label job should not swallow cancellation reported as IllegalStateException",
        )
        assertTrue(
            ioContextIndex > cancellationRethrowIndex,
            "$label dispatcher switch should be inside the guarded work",
        )
        assertTrue(
            outerCatchIndex > ioContextIndex,
            "$label job should catch cancellation outside the dispatcher switch",
        )
        assertTrue(
            nonCancellableFinishIndex > outerCatchIndex,
            "$label job should persist interrupted summaries in a non-cancellable context",
        )
    }

    private fun source(path: String): String {
        val root = generateSequence(Path.of(System.getProperty("user.dir"))) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        return Files.readString(root.resolve(path))
    }
}
