package com.shiyu.chat.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shiyu.chat.shiyu
import java.util.concurrent.TimeUnit

class StructuredMemoryWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val repository = applicationContext.shiyu.repository
        val characterId = inputData.getString(INPUT_CHARACTER_ID)
        return try {
            repository.processPendingMemory(characterId)
            if (
                !repository.activeApiKey().isNullOrBlank() &&
                repository.hasQueuedMemoryJobs()
            ) {
                Result.retry()
            } else {
                Result.success()
            }
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val INPUT_CHARACTER_ID = "characterId"
        private const val WORK_PREFIX = "structured-memory:"

        fun enqueue(
            context: Context,
            characterId: String,
            delayMillis: Long = 8_000L,
        ) {
            val request = OneTimeWorkRequestBuilder<StructuredMemoryWorker>()
                .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setInputData(workDataOf(INPUT_CHARACTER_ID to characterId))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_PREFIX + characterId,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}
