package io.github.haku4130.noscrollguard.evidence

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.PowerManager
import io.github.haku4130.noscrollguard.settings.SecureKeys
import io.github.haku4130.noscrollguard.settings.SecureSettings
import io.github.haku4130.noscrollguard.state.AccessibilityStateReader

/** Collects evidence about the moment of the reset. Repairs nothing. */
class EvidenceCollector(
    private val context: Context,
    private val settings: SecureSettings,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val reader = AccessibilityStateReader(settings)

    fun collect(): Evidence {
        val now = clock()
        return Evidence(
            timestampMs = now,
            masterEnabled = reader.isMasterEnabled(),
            serviceListed = reader.isServiceListed(),
            foregroundApp = foregroundApp(now),
            screenOn = screenOn(),
            lastWriterPackage = SettingsWriterProbe.lastWriterOf(SecureKeys.ACCESSIBILITY_ENABLED)
        )
    }

    private fun foregroundApp(now: Long): String? = try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(now - 60_000L, now)
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                last = event.packageName
            }
        }
        last
    } catch (e: Exception) {
        null
    }

    private fun screenOn(): Boolean = try {
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    } catch (e: Exception) {
        false
    }
}
