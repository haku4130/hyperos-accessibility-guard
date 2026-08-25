package io.github.haku4130.noscrollguard.state

import io.github.haku4130.noscrollguard.Constants
import io.github.haku4130.noscrollguard.settings.SecureKeys
import io.github.haku4130.noscrollguard.settings.SecureSettings

/** Reads accessibility state. Changes nothing. */
class AccessibilityStateReader(
    private val settings: SecureSettings,
    /**
     * Whether the accessibility manager considers the service crashed.
     * null means the answer could not be determined. Injected so the logic is testable.
     */
    private val crashedProbe: () -> Boolean? = { AccessibilityRuntimeProbe.isCrashed() }
) {

    fun isMasterEnabled(): Boolean =
        settings.getInt(SecureKeys.ACCESSIBILITY_ENABLED, 0) == 1

    fun isServiceListed(): Boolean {
        val raw = settings.getString(SecureKeys.ENABLED_SERVICES) ?: return false
        return raw.split(':').any { it.trim() == Constants.NOSCROLL_SERVICE }
    }

    /**
     * Settings-level health only. Says nothing about whether the service actually runs.
     */
    fun isSettingsHealthy(): Boolean = isMasterEnabled() && isServiceListed()

    /**
     * Full health: the settings are right AND the accessibility manager does not consider
     * the service crashed.
     *
     * The crashed-but-configured state is the nastier failure: the settings look perfect,
     * the target app believes it has its permission, and it silently does nothing. It
     * happens after the target app's process exits — the system marks the service crashed
     * but leaves the settings untouched, so a settings-only check sees nothing wrong.
     *
     * When the runtime state cannot be determined, the settings verdict stands — never
     * raise a false alarm on missing information.
     */
    fun isHealthy(): Boolean {
        if (!isSettingsHealthy()) return false
        return crashedProbe() != true
    }
}
