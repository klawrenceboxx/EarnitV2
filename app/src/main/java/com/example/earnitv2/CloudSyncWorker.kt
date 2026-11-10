package com.kaleel.earnitv2

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App-facing entry points for cloud sync. Sync is opt-in: nothing runs until the user signs in. */
internal object CloudSync {
    private const val STALE_AFTER_MILLIS = 15 * 60_000L

    fun backend(): CloudSyncBackend = SupabaseCloudSyncBackend()

    fun engine(context: Context): CloudSyncEngine {
        val appContext = context.applicationContext
        return CloudSyncEngine(
            context = appContext,
            backend = backend(),
            strictModeProtecting = { strictModeProtecting(appContext) }
        )
    }

    fun isSignedIn(context: Context): Boolean = CloudSyncStore(context.applicationContext).session() != null

    /** Queues a sync when signed in and the last successful one is older than 15 minutes. */
    fun syncIfStale(context: Context) {
        val store = CloudSyncStore(context.applicationContext)
        if (store.session() == null) return
        if (System.currentTimeMillis() - store.lastSyncAtMillis() < STALE_AFTER_MILLIS) return
        CloudSyncWorker.syncSoon(context)
    }

    /**
     * Whether Strict Mode currently forbids weakening Rules. If the state cannot be read, assume
     * it does: holding a remote change back is always the safe direction.
     */
    private fun strictModeProtecting(context: Context): Boolean = runCatching {
        val global = GlobalStrictModeStore(SharedPreferencesStrictModeFoundationPersistence(context))
            .globalConfiguration()
            ?.protectsLessRestrictiveChanges() == true
        val legacy = StrictModeStore(SharedPreferencesStrictModePersistence(context))
            .state()
            .lifecycleState
            .isStrictModeProtecting()
        global || legacy
    }.getOrDefault(true)
}

class CloudSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        when (CloudSync.engine(applicationContext).sync().outcome) {
            CloudSyncOutcome.Offline -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val PERIODIC_WORK = "cloud_sync_periodic"
        private const val ONE_TIME_WORK = "cloud_sync_now"

        private fun networkConstraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Keeps a background sync running every six hours while signed in. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun syncSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
                .setConstraints(networkConstraints())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(ONE_TIME_WORK, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.cancelUniqueWork(PERIODIC_WORK)
            workManager.cancelUniqueWork(ONE_TIME_WORK)
        }
    }
}
