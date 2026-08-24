package io.github.haku4130.noscrollguard.evidence

/**
 * Tries to determine which package wrote a secure setting last.
 *
 * The settings database stores a pkg: field, reachable only through dumpsys.
 * The DUMP permission is granted via pm grant, but the call may still be blocked
 * by SELinux for a regular app. Hence best-effort: any failure yields null and
 * the guard keeps working.
 *
 * On Xiaomi 14T Pro / HyperOS 3 this call is NOT blocked and returns the real
 * writer package.
 */
object SettingsWriterProbe {

    fun lastWriterOf(key: String): String? = try {
        val process = ProcessBuilder("dumpsys", "settings")
            .redirectErrorStream(true)
            .start()
        val line = process.inputStream.bufferedReader().useLines { lines ->
            lines.firstOrNull { it.contains("name:$key ") }
        }
        process.destroy()
        line?.let { Regex("pkg:(\\S+)").find(it)?.groupValues?.get(1) }
    } catch (e: Exception) {
        null
    }
}
