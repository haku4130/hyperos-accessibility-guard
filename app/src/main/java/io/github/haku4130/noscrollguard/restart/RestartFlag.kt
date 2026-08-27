package io.github.haku4130.noscrollguard.restart

private const val KEY_PENDING = "restart_pending_since_ms"

/**
 * Remembers that the guarded app needs reopening.
 *
 * Repairing the permission is not the same as reviving the app: after its process dies
 * the service rebinds and every system-visible signal turns green, while the app itself
 * stays inert until it is opened. The guard cannot restart it — that needs
 * FORCE_STOP_PACKAGES, which is signature|privileged — so the flag survives here until
 * the next unlock, when opening the app is least intrusive.
 *
 * Persisted, because the guard's own process may die in between.
 */
class RestartFlag(
    private val store: MutableMap<String, Long>,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun markNeeded() {
        if (store[KEY_PENDING] == null) store[KEY_PENDING] = clock()
    }

    fun isNeeded(): Boolean = store[KEY_PENDING] != null

    fun clear() {
        store.remove(KEY_PENDING)
    }

    /** How long the app has been waiting to be reopened, or 0 when nothing is pending. */
    fun pendingForMs(): Long {
        val since = store[KEY_PENDING] ?: return 0L
        return (clock() - since).coerceAtLeast(0L)
    }
}
