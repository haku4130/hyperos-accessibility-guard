package io.github.haku4130.noscrollguard.state

import io.github.haku4130.noscrollguard.Constants

/**
 * Asks the accessibility manager what it actually thinks of the service, which the
 * secure settings do not reveal.
 *
 * The failure mode this exists for: the settings look perfectly healthy
 * (accessibility_enabled = 1, service listed) while the service sits in
 * "Crashed services" and does nothing. That happens after the target app's process
 * exits — the system marks the service crashed but leaves the settings alone, so a
 * settings-only health check reports everything is fine.
 *
 * Needs the DUMP permission, and SELinux may still refuse the call, so every answer
 * is a tri-state: true / false / unknown (null).
 */
object AccessibilityRuntimeProbe {

    /** @return true if crashed, false if not, null if the state could not be determined. */
    fun isCrashed(): Boolean? = section("Crashed services:")?.contains(Constants.NOSCROLL_SERVICE)

    /** @return true if bound, false if not, null if the state could not be determined. */
    fun isBound(): Boolean? = section("Bound services:")?.contains(Constants.NOSCROLL_PACKAGE)

    private fun section(marker: String): String? = try {
        val process = ProcessBuilder("dumpsys", "accessibility")
            .redirectErrorStream(true)
            .start()
        val line = process.inputStream.bufferedReader().useLines { lines ->
            lines.firstOrNull { it.trimStart().startsWith(marker) }
        }
        process.destroy()
        line
    } catch (e: Exception) {
        null
    }
}
