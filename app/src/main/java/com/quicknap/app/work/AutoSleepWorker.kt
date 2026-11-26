package com.quicknap.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.quicknap.app.QuicknapApp
import java.util.concurrent.TimeUnit

/** 1日1回（おおよそ24時間ごと）、期限を過ぎたアプリを自動で眠らせる */
class AutoSleepWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as QuicknapApp
        if (!app.store.current().setupDone) return Result.success()
        app.sleepController.runAutoSleep(shizukuWaitMs = SHIZUKU_WAIT_MS)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "auto-sleep-daily"
        private const val SHIZUKU_WAIT_MS = 5_000L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoSleepWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
