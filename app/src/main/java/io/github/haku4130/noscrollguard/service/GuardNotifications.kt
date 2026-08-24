package io.github.haku4130.noscrollguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import io.github.haku4130.noscrollguard.R

object GuardNotifications {

    const val CHANNEL_ONGOING = "guard_ongoing"
    const val CHANNEL_EVENTS = "guard_events"
    const val ID_ONGOING = 1
    const val ID_EVENT = 2

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Guard running", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EVENTS, "Resets caught", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    fun ongoing(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setContentTitle(context.getString(R.string.ongoing_title))
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()

    fun notifyRepair(context: Context, text: String) {
        val n = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setContentTitle(context.getString(R.string.repair_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(ID_EVENT, n)
    }
}
