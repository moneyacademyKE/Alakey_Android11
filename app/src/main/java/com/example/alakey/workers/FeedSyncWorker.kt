package com.example.alakey.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.alakey.data.UniversalRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class FeedSyncWorker @AssistedInject constructor(
    @Assisted c: Context,
    @Assisted p: WorkerParameters,
    private val repo: UniversalRepository
) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result = try {
        repo.syncAll()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }

    companion object {
        fun schedule(c: Context) {
            // M6: offline windows previously burned retries on guaranteed
            // failure — periodic sync now requires a connection.
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build()
            WorkManager.getInstance(c).enqueueUniquePeriodicWork(
                "sync",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FeedSyncWorker>(3, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()
            )
        }
    }
}
