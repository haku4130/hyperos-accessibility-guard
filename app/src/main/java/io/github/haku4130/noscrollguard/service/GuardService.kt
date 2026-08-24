package io.github.haku4130.noscrollguard.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import io.github.haku4130.noscrollguard.GuardApp
import io.github.haku4130.noscrollguard.evidence.EvidenceCollector
import io.github.haku4130.noscrollguard.repair.AccessibilityRepairer
import io.github.haku4130.noscrollguard.repair.RepairResult
import io.github.haku4130.noscrollguard.settings.AndroidSecureSettings
import io.github.haku4130.noscrollguard.settings.SecureKeys
import io.github.haku4130.noscrollguard.state.AccessibilityStateReader
import io.github.haku4130.noscrollguard.work.HealthWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

class GuardService : Service() {

    private lateinit var observer: ContentObserver

    override fun onCreate() {
        super.onCreate()
        GuardNotifications.ensureChannels(this)
        startForeground(GuardNotifications.ID_ONGOING, GuardNotifications.ongoing(this))
        HealthWorker.schedule(this)

        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                thread { checkAndRepair(this@GuardService, "observer") }
            }
        }
        listOf(SecureKeys.ACCESSIBILITY_ENABLED, SecureKeys.ENABLED_SERVICES).forEach { key ->
            contentResolver.registerContentObserver(
                Settings.Secure.getUriFor(key), false, observer
            )
        }

        // The observer only sees changes. If the setting was broken while the guard was
        // dead (reboot, force-stop, memory cleanup) there is no event left to catch,
        // so check the state immediately on start.
        thread { checkAndRepair(this, "startup") }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        contentResolver.unregisterContentObserver(observer)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {

        /** A repair is running right now — ignore our own writes. */
        private val repairing = AtomicBoolean(false)

        /** When the last repair finished: events right after it are echoes of our writes. */
        private val settledAt = AtomicLong(0L)

        /** Quiet window after a repair, long enough for the observer to drain our writes. */
        private const val QUIET_AFTER_REPAIR_MS = 3000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, GuardService::class.java))
        }

        /**
         * Shared entry point for the observer, the startup check and HealthWorker.
         * Evidence first, repair second — otherwise our own writes overwrite the evidence.
         */
        fun checkAndRepair(context: Context, source: String) {
            // A repair writes the very settings the observer watches. Without these two
            // guards every write would trigger another repair.
            if (repairing.get()) return
            if (System.currentTimeMillis() - settledAt.get() < QUIET_AFTER_REPAIR_MS) return

            val settings = AndroidSecureSettings(context.contentResolver)
            val reader = AccessibilityStateReader(settings)
            if (reader.isHealthy()) return

            Log.i("GuardTrace", "[$source] unhealthy, collecting evidence")
            val log = GuardApp.eventLog(context)
            val evidence = EvidenceCollector(context, settings).collect()
            log.append(evidence.timestampMs, "[$source] reset detected — ${evidence.describe()}")

            if (GuardApp.pauseState(context).isPaused()) {
                log.append(System.currentTimeMillis(), "[$source] paused — standing down")
                return
            }

            if (!repairing.compareAndSet(false, true)) {
                Log.i("GuardTrace", "[$source] repair already running, backing off")
                return
            }
            Log.i("GuardTrace", "[$source] starting repair")
            val result = try {
                AccessibilityRepairer(settings).repair()
            } finally {
                settledAt.set(System.currentTimeMillis())
                repairing.set(false)
            }

            val time = SimpleDateFormat("HH:mm", Locale.US).format(Date(evidence.timestampMs))
            val message = when (result) {
                is RepairResult.Success ->
                    "Reset at $time, restored. " +
                        "Foreground app: ${evidence.foregroundApp ?: "unknown"}. " +
                        "Setting written by: ${evidence.lastWriterPackage ?: "could not determine"}"
                is RepairResult.NoPermission ->
                    "Reset at $time, repair failed: WRITE_SECURE_SETTINGS not granted"
                is RepairResult.Failed ->
                    "Reset at $time, repair failed: ${result.reason}"
            }
            Log.i("GuardTrace", "[$source] repair finished: $result")
            log.append(System.currentTimeMillis(), "[$source] $message")
            GuardNotifications.notifyRepair(context, message)
        }
    }
}
