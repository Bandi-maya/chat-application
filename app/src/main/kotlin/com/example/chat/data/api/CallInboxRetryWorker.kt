package com.example.chat.data.api

import android.content.Context
import androidx.work.*
import com.example.chat.ServiceLocator
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Best-effort durable recovery for one already-authorized account inbox operation, never an account switch.
 * Android may defer this beyond a call's lifetime. Expired work cannot ring or answer a call. */
class CallInboxRetryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val hash = inputData.getString(ACCOUNT) ?: return Result.failure()
        if (!hash.matches(Regex("[a-f0-9]{64}"))) return Result.failure()
        // KEEP may reuse a pending job from an earlier offer. Read the latest persisted bounded horizon.
        val deadline = maxOf(inputData.getLong(DEADLINE, 0L),
            applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(hash, 0L))
        if (System.currentTimeMillis() >= deadline) return Result.success()
        return try {
            ServiceLocator.ensurePushReady(applicationContext)
            val needsRetry = ServiceLocator.retryDurableCallInboxFromWorker(hash)
            if (needsRetry && runAttemptCount < 4 && System.currentTimeMillis() < deadline) Result.retry() else Result.success()
        } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
        catch (_: Exception) { if (runAttemptCount < 4 && System.currentTimeMillis() < deadline) Result.retry() else Result.success() }
    }
    companion object {
        private const val PREFS = "chaty_call_inbox_recovery_deadlines_v1"
        private const val ACCOUNT = "account_hash"
        private const val DEADLINE = "expires_at_ms"
        private fun name(hash: String) = "chaty_call_inbox_retry_$hash"
        /** Persist recovery before fetching/staging/ACK. No bearer, push body, caller name or SDP in WorkManager. */
        suspend fun schedule(context: Context, hash: String) = withContext(Dispatchers.IO) {
            require(hash.matches(Regex("[a-f0-9]{64}")))
            val deadline = System.currentTimeMillis() + 65_000L
            check(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong(hash, deadline).commit()) {
                "Call recovery deadline could not be saved."
            }
            val request = OneTimeWorkRequestBuilder<CallInboxRetryWorker>()
                .setInputData(workDataOf(ACCOUNT to hash, DEADLINE to deadline))
                .setInitialDelay(10, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(name(hash), ExistingWorkPolicy.KEEP, request).result.get()
        }
        fun cancel(context: Context, hash: String) { WorkManager.getInstance(context.applicationContext).cancelUniqueWork(name(hash)) }
    }
}
