package com.example.chat.data.api

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.chat.ServiceLocator
import java.util.concurrent.TimeUnit

/** Keeps the session alive while the app is never opened (Bandi, 2026-11-07):
 *  every 30 days, silently renew the token pair. Each success slides the
 *  server's 180-day idle window forward, so the user stays signed in
 *  indefinitely. Network errors retry with backoff and never sign out; only
 *  a definitive server rejection (revoke / reuse detection) ends a session.
 *
 *  Battery-friendly by construction: one network-constrained job per 30
 *  days, batched by WorkManager with the device's other maintenance work. */
class SessionRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val backend = ServiceLocator.apiBackend
        return try {
            when (backend.refreshAllRegisteredAccountsInBackground()) {
                ApiBackendService.SessionRefreshOutcome.UNAVAILABLE ->
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
                else -> Result.success()
            }
        } catch (_: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "chaty_session_refresh"
        private const val MAX_ATTEMPTS = 5

        /** Refresh cadence while the app stays closed. The server idle window
         *  is 180 days, so WorkManager's doze-mode deferral of long-period
         *  jobs still leaves weeks of margin. */
        private const val INTERVAL_DAYS = 30L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SessionRefreshWorker>(INTERVAL_DAYS, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(INTERVAL_DAYS, TimeUnit.DAYS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 12, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
