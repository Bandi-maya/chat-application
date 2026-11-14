package com.example.chat.data.api

import android.content.Context
import androidx.work.*
import com.example.chat.ServiceLocator
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Recovery of the already persisted active-account outbox. Never logs in, switches account, or releases held messages. */
class MessageOutboxRetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val hash = inputData.getString(ACCOUNT) ?: return Result.failure()
        if (!hash.matches(Regex("[a-f0-9]{64}"))) return Result.failure()
        return try {
            ServiceLocator.ensurePushReady(applicationContext)
            if (ServiceLocator.apiBackend.retryActiveOutboxFromWorker(hash) && runAttemptCount < 6) Result.retry() else Result.success()
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { if (runAttemptCount < 6) Result.retry() else Result.failure() }
    }
    companion object {
        private const val ACCOUNT = "account_hash"
        fun cancel(context: Context, hash: String) {
            require(hash.matches(Regex("[a-f0-9]{64}")))
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork("chaty_message_outbox_retry_$hash")
        }
        /** Work data contains a non-secret account namespace only. Content and tokens remain in encrypted stores. */
        suspend fun schedule(context: Context, hash: String) = withContext(Dispatchers.IO) {
            require(hash.matches(Regex("[a-f0-9]{64}")))
            val request = OneTimeWorkRequestBuilder<MessageOutboxRetryWorker>()
                .setInputData(workDataOf(ACCOUNT to hash))
                .setInitialDelay(10, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork("chaty_message_outbox_retry_$hash", ExistingWorkPolicy.KEEP, request).result.get()
        }
    }
}
