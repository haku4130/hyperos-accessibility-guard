package io.github.haku4130.noscrollguard.evidence

data class Evidence(
    val timestampMs: Long,
    val masterEnabled: Boolean,
    val serviceListed: Boolean,
    val foregroundApp: String?,
    val screenOn: Boolean,
    val lastWriterPackage: String?
) {
    fun describe(): String = buildString {
        append("master=").append(if (masterEnabled) "on" else "off")
        append(", service listed=").append(if (serviceListed) "yes" else "no")
        append(", screen=").append(if (screenOn) "on" else "off")
        append(", foreground app=").append(foregroundApp ?: "unknown")
        append(", setting written by=").append(lastWriterPackage ?: "could not determine")
    }
}
