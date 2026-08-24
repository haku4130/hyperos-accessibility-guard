package io.github.haku4130.noscrollguard

import io.github.haku4130.noscrollguard.settings.SecureSettings

class FakeSecureSettings(
    val values: MutableMap<String, String> = mutableMapOf()
) : SecureSettings {

    /** Write history in order — tests assert the rebind cycle sequence against it. */
    val writes: MutableList<Pair<String, String>> = mutableListOf()

    /** When true every write fails — simulates a missing WRITE_SECURE_SETTINGS. */
    var writesFail: Boolean = false

    override fun getString(key: String): String? = values[key]

    override fun getInt(key: String, default: Int): Int =
        values[key]?.toIntOrNull() ?: default

    override fun putString(key: String, value: String): Boolean {
        if (writesFail) return false
        values[key] = value
        writes += key to value
        return true
    }

    override fun putInt(key: String, value: Int): Boolean =
        putString(key, value.toString())
}
