package io.github.haku4130.noscrollguard.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.haku4130.noscrollguard.service.GuardService
import java.util.concurrent.TimeUnit

/**
 * Backstop for two cases: the service ends up in a crashed state without the setting
 * changing, and the observer itself dying.
 */
class HealthWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        GuardService.start(applicationContext)
        GuardService.checkAndRepair(applicationContext, "health check")
        return Result.success()
    }

    companion object {
        const val NAME = "health"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<HealthWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }
    }
}
