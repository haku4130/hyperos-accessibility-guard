package io.github.haku4130.noscrollguard

import io.github.haku4130.noscrollguard.repair.AccessibilityRepairer
import io.github.haku4130.noscrollguard.repair.REBIND_PAUSE_MS
import io.github.haku4130.noscrollguard.repair.RepairResult
import io.github.haku4130.noscrollguard.settings.SecureKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class AccessibilityRepairerTest {

    @Test
    fun `rebind cycle writes settings in the correct order`() {
        val fake = FakeSecureSettings()
        fake.values[SecureKeys.ACCESSIBILITY_ENABLED] = "0"
        fake.values[SecureKeys.ENABLED_SERVICES] = Constants.NOSCROLL_SERVICE

        val repairer = AccessibilityRepairer(fake, sleeper = {})
        val result = repairer.repair()

        assertEquals(RepairResult.Success, result)
        assertEquals(
            listOf(
                SecureKeys.ACCESSIBILITY_ENABLED to "0",
                SecureKeys.ENABLED_SERVICES to "",
                SecureKeys.ENABLED_SERVICES to Constants.NOSCROLL_SERVICE,
                SecureKeys.ACCESSIBILITY_ENABLED to "1"
            ),
            fake.writes
        )
    }

    @Test
    fun `cycle sleeps exactly once for the required duration`() {
        val fake = FakeSecureSettings()
        val slept = mutableListOf<Long>()
        AccessibilityRepairer(fake, sleeper = { slept += it }).repair()
        assertEquals(listOf(REBIND_PAUSE_MS), slept)
    }

    @Test
    fun `returns NoPermission and stops when writes are denied`() {
        val fake = FakeSecureSettings()
        fake.writesFail = true

        val result = AccessibilityRepairer(fake, sleeper = {}).repair()

        assertEquals(RepairResult.NoPermission, result)
        assertEquals(emptyList<Pair<String, String>>(), fake.writes)
    }

    @Test
    fun `state is healthy after a repair`() {
        val fake = FakeSecureSettings()
        AccessibilityRepairer(fake, sleeper = {}).repair()

        assertEquals("1", fake.values[SecureKeys.ACCESSIBILITY_ENABLED])
        assertEquals(Constants.NOSCROLL_SERVICE, fake.values[SecureKeys.ENABLED_SERVICES])
    }
}
