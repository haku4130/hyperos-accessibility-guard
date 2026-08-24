package io.github.haku4130.noscrollguard

import io.github.haku4130.noscrollguard.pause.DEFAULT_PAUSE_MS
import io.github.haku4130.noscrollguard.pause.PauseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseStateTest {

    private var now = 1_000_000L
    private val store = mutableMapOf<String, Long>()
    private fun state() = PauseState(store) { now }

    @Test
    fun `not paused by default`() {
        assertFalse(state().isPaused())
    }

    @Test
    fun `is paused after pauseFor`() {
        state().pauseFor(DEFAULT_PAUSE_MS)
        assertTrue(state().isPaused())
    }

    @Test
    fun `pause expires with time`() {
        state().pauseFor(1000L)
        now += 1001L
        assertFalse(state().isPaused())
    }

    @Test
    fun `resume cancels the pause early`() {
        val s = state()
        s.pauseFor(DEFAULT_PAUSE_MS)
        s.resume()
        assertFalse(s.isPaused())
    }

    @Test
    fun `reports the correct remaining time`() {
        val s = state()
        s.pauseFor(5000L)
        now += 2000L
        assertEquals(3000L, s.remainingMs())
    }

    @Test
    fun `remaining time is zero when not paused`() {
        assertEquals(0L, state().remainingMs())
    }
}
