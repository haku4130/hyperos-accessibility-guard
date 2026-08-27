package io.github.haku4130.noscrollguard

import io.github.haku4130.noscrollguard.restart.RestartFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestartFlagTest {

    private var now = 1_000_000L
    private val store = mutableMapOf<String, Long>()
    private fun flag() = RestartFlag(store) { now }

    @Test
    fun `nothing pending by default`() {
        assertFalse(flag().isNeeded())
        assertEquals(0L, flag().pendingForMs())
    }

    @Test
    fun `marking makes it pending`() {
        flag().markNeeded()
        assertTrue(flag().isNeeded())
    }

    @Test
    fun `clearing resets it`() {
        val f = flag()
        f.markNeeded()
        f.clear()
        assertFalse(f.isNeeded())
    }

    @Test
    fun `survives a new instance - the flag is in the store, not the object`() {
        flag().markNeeded()
        assertTrue(flag().isNeeded())
    }

    @Test
    fun `repeated marking keeps the original timestamp`() {
        flag().markNeeded()
        now += 5000L
        flag().markNeeded()
        assertEquals(5000L, flag().pendingForMs())
    }

    @Test
    fun `pending duration is zero once cleared`() {
        val f = flag()
        f.markNeeded()
        now += 5000L
        f.clear()
        assertEquals(0L, f.pendingForMs())
    }
}
