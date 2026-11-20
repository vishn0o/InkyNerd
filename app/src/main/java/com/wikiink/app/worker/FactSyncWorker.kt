package com.wikiink.app.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wikiink.app.appContainer
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/** Prefetches and caches 30+ facts for offline widget + reader use. */
class FactSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val inserted = applicationContext.appContainer.repository.syncFacts()
        Log.i(TAG, "Fact sync complete: $inserted new facts")
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Fact sync failed (attempt $runAttemptCount)", e)
        if (runAttemptCount < 4) Result.retry() else Result.failure()
    }

    companion object {
        private const val TAG = "FactSyncWorker"
        private const val PERIODIC_NAME = "wikiink-fact-sync-periodic"
        private const val IMMEDIATE_NAME = "wikiink-fact-sync-now"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Background refresh also waits for a healthy battery; a user-requested sync doesn't. */
        private val periodicConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<FactSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(periodicConstraints)
                // The immediate one-time sync covers first launch; avoid a duplicate concurrent fetch.
                .setInitialDelay(12, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<FactSyncWorker>()
                .setConstraints(networkConstraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(IMMEDIATE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
