package io.github.haku4130.noscrollguard.state

import io.github.haku4130.noscrollguard.Constants
import io.github.haku4130.noscrollguard.settings.SecureKeys
import io.github.haku4130.noscrollguard.settings.SecureSettings

/** Reads accessibility state. Changes nothing. */
class AccessibilityStateReader(private val settings: SecureSettings) {

    fun isMasterEnabled(): Boolean =
        settings.getInt(SecureKeys.ACCESSIBILITY_ENABLED, 0) == 1

    fun isServiceListed(): Boolean {
        val raw = settings.getString(SecureKeys.ENABLED_SERVICES) ?: return false
        return raw.split(':').any { it.trim() == Constants.NOSCROLL_SERVICE }
    }

    fun isHealthy(): Boolean = isMasterEnabled() && isServiceListed()
}
