package io.github.haku4130.noscrollguard

import io.github.haku4130.noscrollguard.log.EventLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EventLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `writes and reads back an entry`() {
        val log = EventLog(tmp.newFile("events.log"))
        log.append(1000L, "сброс пойман")

        val entries = log.read()
        assertEquals(1, entries.size)
        assertEquals(1000L, entries[0].timestampMs)
        assertEquals("сброс пойман", entries[0].message)
    }

    @Test
    fun `preserves entry order`() {
        val log = EventLog(tmp.newFile("events.log"))
        log.append(1L, "первое")
        log.append(2L, "второе")

        assertEquals(listOf("первое", "второе"), log.read().map { it.message })
    }

    @Test
    fun `trims the journal to the maximum size`() {
        val log = EventLog(tmp.newFile("events.log"), maxEntries = 3)
        repeat(5) { log.append(it.toLong(), "событие $it") }

        val entries = log.read()
        assertEquals(3, entries.size)
        assertEquals(listOf("событие 2", "событие 3", "событие 4"), entries.map { it.message })
    }

    @Test
    fun `a newline in the message does not break parsing`() {
        val log = EventLog(tmp.newFile("events.log"))
        log.append(1L, "строка одна\nстрока два")

        val entries = log.read()
        assertEquals(1, entries.size)
        assertTrue(entries[0].message.contains("строка два"))
        assertFalse(entries[0].message.contains("\n"))
    }

    @Test
    fun `reads an empty file without crashing`() {
        val log = EventLog(tmp.newFile("events.log"))
        assertEquals(emptyList<Any>(), log.read())
    }
}
