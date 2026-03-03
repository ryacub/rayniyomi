package eu.kanade.domain.track.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import eu.kanade.tachiyomi.util.system.workManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.TimeUnit

class PeriodicTrackerSyncJob(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val coordinator = Injekt.get<TrackerSyncCoordinator>()
        coordinator.await(TrackerSyncTrigger.PERIODIC)
        return Result.success()
    }

    companion object {
        private const val TAG = "PeriodicTrackerSync"

        fun setupTask(context: Context) {
            val trackPreferences = Injekt.get<TrackPreferences>()
            if (!trackPreferences.trackerSyncEnabled().get()) {
                context.workManager.cancelUniqueWork(TAG)
                return
            }

            val intervalHours = trackPreferences.trackerSyncIntervalHours().get().coerceAtLeast(1)
            val constraints = Constraints(requiredNetworkType = NetworkType.CONNECTED)
            val request = PeriodicWorkRequestBuilder<PeriodicTrackerSyncJob>(
                intervalHours.toLong(),
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .setInputData(workDataOf(ImmediateTrackerSyncJob.INPUT_TRIGGER to TrackerSyncTrigger.PERIODIC.name))
                .addTag(TAG)
                .build()

            context.workManager.enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
