package io.github.haku4130.noscrollguard.settings

object SecureKeys {
    const val ACCESSIBILITY_ENABLED = "accessibility_enabled"
    const val ENABLED_SERVICES = "enabled_accessibility_services"
}

/** Access to Settings.Secure, behind an interface so the logic is testable without a device. */
interface SecureSettings {
    fun getString(key: String): String?
    fun getInt(key: String, default: Int): Int
    /** @return false if the write failed (no WRITE_SECURE_SETTINGS). */
    fun putString(key: String, value: String): Boolean
    fun putInt(key: String, value: Int): Boolean
}
