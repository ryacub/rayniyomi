package eu.kanade.tachiyomi.ui.recovery

import eu.kanade.tachiyomi.test.VirtualTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.data.recovery.MangaRecoveryApplierImpl
import tachiyomi.data.recovery.MangaRecoveryDb
import tachiyomi.data.recovery.MangaRecoveryRepositoryImpl
import tachiyomi.domain.recovery.RecoveryPreparer
import tachiyomi.domain.recovery.RecoverySourceSearch
import tachiyomi.domain.recovery.model.NewRecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryApplyRequest
import tachiyomi.domain.recovery.model.RecoveryCandidate
import tachiyomi.domain.recovery.model.RecoveryDestination
import tachiyomi.domain.recovery.model.RecoveryEntry
import tachiyomi.domain.recovery.model.RecoveryIdentity
import tachiyomi.domain.recovery.model.RecoveryItem
import tachiyomi.domain.recovery.model.RecoveryItemPair
import tachiyomi.domain.recovery.model.RecoverySession
import tachiyomi.domain.recovery.model.RecoveryState
import tachiyomi.domain.recovery.repository.RecoveryApplier

class RecoveryScreenModelTest {

    private val vt = VirtualTime()

    @BeforeEach
    fun setUp() = vt.setUpMain()

    @AfterEach
    fun tearDown() = vt.tearDownMain()

    private class FakeBackend(
        db: MangaRecoveryDb,
        private val originalsList: List<RecoveryOriginal>,
        override val search: RecoverySourceSearch,
        private val seasonCandidates: Set<String> = emptySet(),
        applier: RecoveryApplier? = null,
    ) : RecoveryBackend {
        override val repository = MangaRecoveryRepositoryImpl(db.handler)
        override val applier: RecoveryApplier = applier ?: MangaRecoveryApplierImpl(db.handler)
        override val preparer = object : RecoveryPreparer {
            override suspend fun identities(originalIds: List<Long>) =
                originalIds.associateWith { RecoveryIdentity(sourceId = 1, url = "url-$it") }

            override suspend fun prepare(entry: RecoveryEntry, session: RecoverySession) = RecoveryApplyRequest(
                entryId = entry.id,
                runToken = checkNotNull(entry.runToken),
                originalId = entry.originalId,
                destination = destination(checkNotNull(entry.choice)),
                pairs = entry.pairs,
                tracks = emptyList(),
                keepOriginal = session.keepOriginal,
                now = 5_000,
            )
        }
        val coverCopies = mutableListOf<Long>()

        private fun destination(candidate: RecoveryCandidate) = RecoveryDestination(
            sourceId = candidate.sourceId,
            url = candidate.url,
            title = candidate.title,
            items = listOf(RecoveryItem("d1", "Chapter 1", 1.0), RecoveryItem("d2", "Chapter 2", 2.0)),
            seasons = candidate.url in seasonCandidates,
        )

        override suspend fun originals(sourceId: Long?, originalIds: List<Long>) = originalsList

        override suspend fun destinationSources(excludeSourceId: Long?) =
            listOf(RecoverySourceOption(20, "Source A", "en"), RecoverySourceOption(30, "Source B", "en"))

        override suspend fun preview(originalId: Long, candidate: RecoveryCandidate) = RecoveryPreview(
            destination = destination(candidate),
            pairs = listOf(RecoveryItemPair("o1", "d1", copyPosition = false)),
            originalItemCount = 2,
        )

        override suspend fun copyCover(originalId: Long, destinationId: Long) {
            coverCopies += destinationId
        }
    }

    private fun original(id: Long, seasons: Boolean = false) =
        RecoveryOriginal(id = id, title = "Title $id", thumbnailUrl = null, sourceId = 1, seasons = seasons)

    private fun results(vararg urls: String) = RecoverySourceSearch { sourceId, _ ->
        if (sourceId == 20L) urls.map { RecoveryCandidate(20, it, it) } else emptyList()
    }

    private fun MangaRecoveryDb.seed(vararg ids: Long) {
        ids.forEach { insertManga(it) }
        ids.forEach { insertChapter(it * 10 + 1, it, "o1", 1.0, read = true) }
    }

    private fun model(backend: RecoveryBackend, sourceId: Long? = 1) =
        RecoveryScreenModel(RecoveryMedia.MANGA, sourceId, emptyList(), backend, now = { 1_000 })

    private suspend fun RecoveryScreenModel.awaitState(predicate: (RecoveryScreenModel.State) -> Boolean) =
        withTimeout(10_000) { state.first(predicate) }

    @Test
    fun `an unfinished session resumes at review instead of starting over`() = runTest {
        MangaRecoveryDb().use { db ->
            db.seed(1)
            val backend = FakeBackend(db, listOf(original(1)), results())
            val sessionId = backend.repository.createSession(
                listOf(20L),
                true,
                listOf(NewRecoveryEntry(1, "Title 1", RecoveryState.NEEDS_REVIEW)),
                500,
            )

            val model = model(backend)
            val state = model.awaitState { it.step == RecoveryScreenModel.Step.Review }

            assertEquals(sessionId, state.sessionId)
        }
    }

    @Test
    fun `find replacements searches all selected titles once and marks season titles unsupported`() = runTest {
        MangaRecoveryDb().use { db ->
            db.seed(1, 2, 3)
            val queries = mutableListOf<String>()
            val search = RecoverySourceSearch { sourceId, query ->
                synchronized(queries) { if (sourceId == 20L) queries += query }
                listOf(RecoveryCandidate(sourceId, "/$query", query))
            }
            val backend = FakeBackend(db, listOf(original(1), original(2, seasons = true), original(3)), search)
            val model = model(backend)
            model.awaitState { it.step == RecoveryScreenModel.Step.Select }

            model.toggleSource(20)
            model.toggleOriginal(3)
            model.findReplacements()
            val state = model.awaitState { s -> s.step == RecoveryScreenModel.Step.Review && s.entries.isNotEmpty() && s.busy == null }

            assertEquals(listOf("Title 1"), queries)
            assertEquals(
                listOf(RecoveryState.NEEDS_REVIEW, RecoveryState.UNSUPPORTED),
                state.entries.sortedBy { it.originalId }.map { it.state },
            )
        }
    }

    @Test
    fun `a previewed candidate proposes pairs and approval makes the row ready`() = runTest {
        MangaRecoveryDb().use { db ->
            db.seed(1)
            val backend = FakeBackend(db, listOf(original(1)), results("/m/1"))
            val model = model(backend)
            model.awaitState { it.step == RecoveryScreenModel.Step.Select }
            model.toggleSource(20)
            model.findReplacements()
            val entry = model.awaitState { s -> s.entries.any { it.state == RecoveryState.NEEDS_REVIEW } }.entries.single()

            model.choose(entry.id, entry.candidates.single())
            val previewed = model.awaitState { entry.id in it.previews }
            model.approve(entry.id)
            val approved = model.awaitState { s -> s.entries.single().state == RecoveryState.READY }

            assertEquals(listOf(RecoveryItemPair("o1", "d1", false)), previewed.previews.getValue(entry.id).pairs)
            assertEquals(RecoveryCandidate(20, "/m/1", "/m/1"), approved.entries.single().choice)
        }
    }

    @Test
    fun `a season-type candidate cannot be approved`() = runTest {
        MangaRecoveryDb().use { db ->
            db.seed(1)
            val backend = FakeBackend(db, listOf(original(1)), results("/s/1"), seasonCandidates = setOf("/s/1"))
            val model = model(backend)
            model.awaitState { it.step == RecoveryScreenModel.Step.Select }
            model.toggleSource(20)
            model.findReplacements()
            val entry = model.awaitState { s -> s.entries.any { it.state == RecoveryState.NEEDS_REVIEW } }.entries.single()

            model.choose(entry.id, entry.candidates.single())
            val previewed = model.awaitState { entry.id in it.previews }
            model.approve(entry.id)

            assertFalse(previewed.canApprove(entry.id))
            assertEquals(RecoveryState.NEEDS_REVIEW, backend.repository.getEntries(entry.sessionId).single().state)
        }
    }

    @Test
    fun `apply shows a partial batch as partial and copies covers for completed rows`() = runTest {
        MangaRecoveryDb().use { db ->
            db.seed(1, 2)
            val backend = FakeBackend(db, listOf(original(1), original(2)), results("/m/x"))
            val model = model(backend)
            model.awaitState { it.step == RecoveryScreenModel.Step.Select }
            model.toggleSource(20)
            model.findReplacements()
            val entries = model.awaitState { s -> s.entries.size == 2 && s.entries.all { it.state == RecoveryState.NEEDS_REVIEW } }.entries
            val first = entries.single { it.originalId == 1L }
            model.choose(first.id, first.candidates.single())
            model.awaitState { first.id in it.previews }
            model.approve(first.id)
            model.awaitState { s -> s.entries.any { it.state == RecoveryState.READY } }

            model.applySelected()
            val result = model.awaitState { it.step == RecoveryScreenModel.Step.Result }

            assertEquals(1, result.summary.completed)
            assertEquals(1, result.summary.unresolved)
            assertFalse(result.summary.fullyRecovered)
            assertEquals(1, backend.coverCopies.size)
        }
    }

    @Test
    fun `cancel during apply keeps finished rows and leaves the rest resumable`() = runTest {
        MangaRecoveryDb().use { db ->
            db.seed(1)
            val started = CompletableDeferred<Unit>()
            val blocking = RecoveryApplier {
                started.complete(Unit)
                awaitCancellation()
            }
            val backend = FakeBackend(db, listOf(original(1)), results("/m/1"), applier = blocking)
            val model = model(backend)
            model.awaitState { it.step == RecoveryScreenModel.Step.Select }
            model.toggleSource(20)
            model.findReplacements()
            val entry = model.awaitState { s -> s.entries.any { it.state == RecoveryState.NEEDS_REVIEW } }.entries.single()
            model.choose(entry.id, entry.candidates.single())
            model.awaitState { entry.id in it.previews }
            model.approve(entry.id)
            model.awaitState { s -> s.entries.single().state == RecoveryState.READY }

            model.applySelected()
            started.await()
            model.cancel()
            val state = model.awaitState { s -> s.busy == null && s.entries.single().state == RecoveryState.INTERRUPTED }

            assertTrue(state.summary.unresolved == 1)
        }
    }

    @Test
    fun `a manual search replaces the candidates of one row only`() = runTest {
        MangaRecoveryDb().use { db ->
            db.seed(1, 2)
            val search = RecoverySourceSearch { sourceId, query ->
                if (sourceId == 20L) listOf(RecoveryCandidate(20, "/q/$query", query)) else emptyList()
            }
            val backend = FakeBackend(db, listOf(original(1), original(2)), search)
            val model = model(backend)
            model.awaitState { it.step == RecoveryScreenModel.Step.Select }
            model.toggleSource(20)
            model.findReplacements()
            val entries = model.awaitState { s -> s.entries.size == 2 && s.entries.all { it.state == RecoveryState.NEEDS_REVIEW } }.entries
            val first = entries.single { it.originalId == 1L }

            model.searchAgain(first.id, "Other name")
            val state = model.awaitState { s -> s.entries.single { it.id == first.id }.candidates.firstOrNull()?.title == "Other name" && s.busy == null }

            assertEquals(listOf("Title 2"), state.entries.single { it.originalId == 2L }.candidates.map { it.title })
        }
    }
}
