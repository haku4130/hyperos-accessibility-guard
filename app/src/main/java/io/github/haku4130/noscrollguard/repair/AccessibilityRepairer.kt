package io.github.haku4130.noscrollguard.repair

import io.github.haku4130.noscrollguard.Constants
import io.github.haku4130.noscrollguard.settings.SecureKeys
import io.github.haku4130.noscrollguard.settings.SecureSettings

/** Pause between clearing the service list and writing it back. Verified on device. */
const val REBIND_PAUSE_MS = 2000L

sealed class RepairResult {
    object Success : RepairResult()
    object NoPermission : RepairResult()
    data class Failed(val reason: String) : RepairResult()
}

/**
 * Restores the NoScroll accessibility service.
 *
 * Writing accessibility_enabled = 1 alone is not enough: the service stays in
 * "Crashed services" and never binds. The full rebind cycle is required.
 */
class AccessibilityRepairer(
    private val settings: SecureSettings,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) }
) {

    fun repair(): RepairResult {
        if (!settings.putInt(SecureKeys.ACCESSIBILITY_ENABLED, 0)) {
            return RepairResult.NoPermission
        }
        if (!settings.putString(SecureKeys.ENABLED_SERVICES, "")) {
            return RepairResult.Failed("could not clear the service list")
        }

        sleeper(REBIND_PAUSE_MS)

        if (!settings.putString(SecureKeys.ENABLED_SERVICES, Constants.NOSCROLL_SERVICE)) {
            return RepairResult.Failed("could not restore the service into the list")
        }
        if (!settings.putInt(SecureKeys.ACCESSIBILITY_ENABLED, 1)) {
            return RepairResult.Failed("could not turn the master switch back on")
        }
        return RepairResult.Success
    }
}
