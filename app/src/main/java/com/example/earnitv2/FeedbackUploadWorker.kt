package com.example.earnitv2

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class FeedbackUploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val queueId = inputData.getString("queue_id") ?: return Result.failure()
        return when (FeedbackRepository(applicationContext).retry(queueId)) {
            is FeedbackSubmitResult.Success -> Result.success()
            is FeedbackSubmitResult.PermanentFailure, FeedbackSubmitResult.MissingConfiguration -> Result.failure()
            else -> Result.retry()
        }
    }
}
