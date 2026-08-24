package io.github.haku4130.noscrollguard

import io.github.haku4130.noscrollguard.settings.SecureKeys
import io.github.haku4130.noscrollguard.state.AccessibilityStateReader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityStateReaderTest {

    private fun reader(enabled: String?, services: String?): AccessibilityStateReader {
        val fake = FakeSecureSettings()
        enabled?.let { fake.values[SecureKeys.ACCESSIBILITY_ENABLED] = it }
        services?.let { fake.values[SecureKeys.ENABLED_SERVICES] = it }
        return AccessibilityStateReader(fake)
    }

    @Test
    fun `healthy when master switch is on and service is listed`() {
        val r = reader("1", Constants.NOSCROLL_SERVICE)
        assertTrue(r.isMasterEnabled())
        assertTrue(r.isServiceListed())
        assertTrue(r.isHealthy())
    }

    @Test
    fun `unhealthy when master switch is off`() {
        val r = reader("0", Constants.NOSCROLL_SERVICE)
        assertFalse(r.isMasterEnabled())
        assertFalse(r.isHealthy())
    }

    @Test
    fun `unhealthy when service is missing from the list`() {
        val r = reader("1", "com.other/com.other.Service")
        assertFalse(r.isServiceListed())
        assertFalse(r.isHealthy())
    }

    @Test
    fun `finds the service among several colon separated entries`() {
        val list = "com.other/com.other.Service:${Constants.NOSCROLL_SERVICE}"
        val r = reader("1", list)
        assertTrue(r.isServiceListed())
        assertTrue(r.isHealthy())
    }

    @Test
    fun `unhealthy and does not crash when settings are absent`() {
        val r = reader(null, null)
        assertFalse(r.isMasterEnabled())
        assertFalse(r.isServiceListed())
        assertFalse(r.isHealthy())
    }
}
