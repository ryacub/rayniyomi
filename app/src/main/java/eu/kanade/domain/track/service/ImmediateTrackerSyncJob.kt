package eu.kanade.domain.track.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.di.appGraph
import eu.kanade.tachiyomi.util.system.workManager

class ImmediateTrackerSyncJob(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val trigger = runCatching {
            TrackerSyncTrigger.valueOf(inputData.getString(INPUT_TRIGGER) ?: TrackerSyncTrigger.MANUAL.name)
        }
            .getOrDefault(TrackerSyncTrigger.MANUAL)

        val coordinator = appGraph.trackerSyncCoordinator
        val result = coordinator.await(trigger)

        return Result.success(
            workDataOf(
                OUTPUT_SYNCED_COUNT to result.syncedItems,
                OUTPUT_FAILED_COUNT to result.failedCount,
            ),
        )
    }

    companion object {
        private const val TAG = "ImmediateTrackerSync"

        const val INPUT_TRIGGER = "input_trigger"
        const val OUTPUT_SYNCED_COUNT = "output_synced_count"
        const val OUTPUT_FAILED_COUNT = "output_failed_count"

        fun startNow(
            context: Context,
            trigger: TrackerSyncTrigger,
        ) {
            val constraints = Constraints(requiredNetworkType = NetworkType.CONNECTED)
            val request = OneTimeWorkRequestBuilder<ImmediateTrackerSyncJob>()
                .setConstraints(constraints)
                .setInputData(workDataOf(INPUT_TRIGGER to trigger.name))
                .addTag(TAG)
                .build()

            context.workManager.enqueueUniqueWork(TAG, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
