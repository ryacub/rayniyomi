package tachiyomi.data.recovery

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import tachiyomi.data.Database
import tachiyomi.data.handlers.manga.MangaDatabaseHandler
import tachiyomi.domain.recovery.model.RecoveryApplyRequest
import tachiyomi.domain.recovery.model.RecoveryApplyResult
import tachiyomi.domain.recovery.model.RecoveryReviewReason
import tachiyomi.domain.recovery.model.RecoveryState
import tachiyomi.domain.recovery.repository.RecoveryApplier

class MangaRecoveryApplierImpl(
    private val handler: MangaDatabaseHandler,
) : RecoveryApplier {

    override suspend fun apply(request: RecoveryApplyRequest): RecoveryApplyResult {
        val result = try {
            handler.await(inTransaction = true) { applyInTransaction(request) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RecoveryReview) {
            RecoveryApplyResult.NeedsReview(e.reason)
        } catch (e: Exception) {
            RecoveryApplyResult.Failed(e.message ?: e::class.simpleName.orEmpty())
        }
        return withContext(NonCancellable) { recordOutcome(request, result) }
    }

    private suspend fun recordOutcome(request: RecoveryApplyRequest, result: RecoveryApplyResult): RecoveryApplyResult {
        val (state, error) = when (result) {
            is RecoveryApplyResult.NeedsReview -> RecoveryState.NEEDS_REVIEW to result.reason.name
            is RecoveryApplyResult.Failed -> RecoveryState.FAILED to result.message
            else -> return result
        }
        handler.await {
            recoveryQueries.finishEntry(
                state = state.name,
                resultId = null,
                error = error,
                entryId = request.entryId,
                runToken = request.runToken,
                applyingState = RecoveryState.APPLYING.name,
            )
        }
        return result
    }

    private fun Database.applyInTransaction(request: RecoveryApplyRequest): RecoveryApplyResult {
        val owner = recoveryQueries.getEntryOwner(request.entryId, request.runToken).executeAsOneOrNull()
        if (owner != RecoveryState.APPLYING.name) return RecoveryApplyResult.NotOwned

        val original = recoveryQueries.getOriginal(request.originalId).executeAsOne()
        val target = request.destination

        val existing = recoveryQueries.findDestination(target.sourceId, target.url).executeAsOneOrNull()
        val destinationId = existing?._id ?: run {
            recoveryQueries.insertDestination(
                source = target.sourceId,
                url = target.url,
                artist = target.artist,
                author = target.author,
                description = target.description,
                genre = target.genre,
                title = target.title,
                status = target.status,
                thumbnailUrl = target.thumbnailUrl,
                viewer = original.viewer,
                chapterFlags = original.chapter_flags,
                dateAdded = request.now,
            )
            recoveryQueries.selectLastInsertedRowId().executeAsOne()
        }
        if (destinationId == request.originalId) throw RecoveryReview(RecoveryReviewReason.SELF_TARGET)

        transferTracks(request, destinationId)
        insertMissingItems(request, destinationId)
        mergePairs(request, destinationId)
        mangas_categoriesQueries.transferCategories(keepMangaId = destinationId, deleteMangaId = request.originalId)

        if (existing?.favorite != true) {
            recoveryQueries.addToLibrary(
                viewer = original.viewer,
                chapterFlags = original.chapter_flags,
                dateAdded = if (request.keepOriginal) request.now else original.date_added,
                mangaId = destinationId,
            )
        }
        if (!request.keepOriginal) recoveryQueries.removeFromLibrary(request.originalId)

        val completed = recoveryQueries.finishEntry(
            state = RecoveryState.COMPLETED.name,
            resultId = destinationId,
            error = null,
            entryId = request.entryId,
            runToken = request.runToken,
            applyingState = RecoveryState.APPLYING.name,
        ).value
        check(completed == 1L) { "Recovery entry ${request.entryId} changed during apply" }
        return RecoveryApplyResult.Completed(destinationId)
    }

    private fun Database.transferTracks(request: RecoveryApplyRequest, destinationId: Long) {
        val destinationTracks = recoveryQueries.getTrackRemoteIds(destinationId).executeAsList()
            .associate { it.sync_id to it.remote_id }
        val conflict = request.tracks.any { track ->
            destinationTracks[track.syncId]?.let { it != track.remoteId } ?: false
        }
        if (conflict) throw RecoveryReview(RecoveryReviewReason.TRACKER_CONFLICT)

        request.tracks.forEach { track ->
            if (track.syncId in destinationTracks) {
                manga_syncQueries.updateProgressIfGreater(
                    lastChapterRead = track.lastProgress,
                    mangaId = destinationId,
                    syncId = track.syncId,
                )
            } else {
                recoveryQueries.insertTrackIfMissing(
                    mangaId = destinationId,
                    syncId = track.syncId,
                    remoteId = track.remoteId,
                    libraryId = track.libraryId,
                    title = track.title,
                    lastProgress = track.lastProgress,
                    totalItems = track.totalItems,
                    status = track.status,
                    score = track.score,
                    remoteUrl = track.remoteUrl,
                    startDate = track.startDate,
                    finishDate = track.finishDate,
                    private = track.private,
                )
            }
        }
    }

    private fun Database.insertMissingItems(request: RecoveryApplyRequest, destinationId: Long) {
        val known = recoveryQueries.getItemUrls(destinationId).executeAsList().toSet()
        request.destination.items.filter { it.url !in known }.forEach { item ->
            recoveryQueries.insertItem(
                mangaId = destinationId,
                url = item.url,
                name = item.name,
                scanlator = item.scanlator,
                number = item.number,
                sourceOrder = item.sourceOrder,
                dateFetch = request.now,
                dateUpload = item.dateUpload,
            )
        }
    }

    private fun Database.mergePairs(request: RecoveryApplyRequest, destinationId: Long) {
        request.pairs.forEach { pair ->
            val from = recoveryQueries.getItemState(request.originalId, pair.originalUrl).executeAsOneOrNull()
            val to = recoveryQueries.getItemState(destinationId, pair.destinationUrl).executeAsOneOrNull()
            if (from == null || to == null) throw RecoveryReview(RecoveryReviewReason.MAPPING_CHANGED)
            recoveryQueries.mergeItemState(
                read = from.read,
                bookmark = from.bookmark,
                itemId = to._id,
            )
            recoveryQueries.mergeHistory(destinationItemId = to._id, originalItemId = from._id)
        }
    }
}

internal class RecoveryReview(val reason: RecoveryReviewReason) : Exception(reason.name)
