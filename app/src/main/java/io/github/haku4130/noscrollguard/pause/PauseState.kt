package io.github.haku4130.noscrollguard.pause

const val DEFAULT_PAUSE_MS = 30 * 60 * 1000L

private const val KEY_UNTIL = "pause_until_ms"

/**
 * Guard pause. The store is injected so the logic can be tested without Android;
 * in the app it is a thin wrapper over SharedPreferences.
 */
class PauseState(
    private val store: MutableMap<String, Long>,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    fun pauseFor(durationMs: Long) {
        store[KEY_UNTIL] = clock() + durationMs
    }

    fun resume() {
        store.remove(KEY_UNTIL)
    }

    fun isPaused(): Boolean = remainingMs() > 0

    fun remainingMs(): Long {
        val until = store[KEY_UNTIL] ?: return 0L
        return (until - clock()).coerceAtLeast(0L)
    }
}
