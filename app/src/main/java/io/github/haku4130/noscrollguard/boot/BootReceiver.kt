package io.github.haku4130.noscrollguard.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.haku4130.noscrollguard.GuardApp
import io.github.haku4130.noscrollguard.service.GuardService
import io.github.haku4130.noscrollguard.work.HealthWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        GuardApp.eventLog(context).append(System.currentTimeMillis(), "[boot] device started, guard brought up")
        GuardService.start(context)
        HealthWorker.schedule(context)
    }
}
