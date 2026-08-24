package io.github.haku4130.noscrollguard

import android.content.Context
import io.github.haku4130.noscrollguard.log.EventLog
import io.github.haku4130.noscrollguard.pause.PauseState
import java.io.File

/** Shared objects. No DI framework — there are only two of them. */
object GuardApp {

    @Volatile
    private var log: EventLog? = null

    /**
     * The journal is a single instance per process. Creating a new object per call
     * defeats the lock inside EventLog: concurrent threads would each read the file
     * into their own copy and overwrite each other's entries.
     */
    fun eventLog(context: Context): EventLog =
        log ?: synchronized(this) {
            log ?: EventLog(File(context.applicationContext.filesDir, "events.log")).also { log = it }
        }

    fun pauseState(context: Context): PauseState {
        val prefs = context.getSharedPreferences("guard", Context.MODE_PRIVATE)
        val store = object : AbstractMutableMap<String, Long>() {
            override val entries: MutableSet<MutableMap.MutableEntry<String, Long>>
                get() = throw UnsupportedOperationException()

            override fun put(key: String, value: Long): Long? {
                prefs.edit().putLong(key, value).apply()
                return null
            }

            override fun get(key: String): Long? =
                if (prefs.contains(key)) prefs.getLong(key, 0L) else null

            override fun remove(key: String): Long? {
                prefs.edit().remove(key).apply()
                return null
            }
        }
        return PauseState(store)
    }
}
